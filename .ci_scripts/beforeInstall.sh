#! /bin/bash

# TODO: Remove

SCRIPT_DIR=`dirname $0 | sed -e "s|^\./|$PWD/|"`

# Install MongoDB
MONGO_HOME="$HOME/mongodb-linux-x86_64-amazon-3.4.10"

if [ ! -x "$MONGO_HOME/bin/mongod" ]; then
    if [ -d "$MONGO_HOME" ]; then
      rm -rf "$MONGO_HOME"
    fi

    cd "$HOME"
    curl -s -o - https://fastdl.mongodb.org/linux/mongodb-linux-x86_64-amazon-3.4.10.tgz | tar -xzf -
    chmod u+x "$MONGO_HOME/bin/mongod"
fi

export PATH="$MONGO_HOME/bin:$PATH"

# OpenSSL
if [ ! -L "$HOME/ssl/lib/libssl.so.1.0.0" ] && [ ! -f "$HOME/ssl/lib/libcrypto.so.1.0.0" ]; then
  echo "[INFO] Building OpenSSL"

  cd /tmp
  curl -s -o - https://www.openssl.org/source/openssl-1.0.1s.tar.gz | tar -xzf -
  cd openssl-1.0.1s
  rm -rf "$HOME/ssl" && mkdir "$HOME/ssl"
  ./config -shared enable-ssl2 --prefix="$HOME/ssl" > /dev/null
  make depend > /dev/null
  make install > /dev/null

  ln -s "$HOME/ssl/lib/libssl.so.1.0.0" "$HOME/ssl/lib/libssl.so.10"
  ln -s "$HOME/ssl/lib/libcrypto.so.1.0.0" "$HOME/ssl/lib/libcrypto.so.10"
fi

export LD_LIBRARY_PATH="$HOME/ssl/lib:$LD_LIBRARY_PATH"

# MongoDB configuration
MONGO_CONF="$SCRIPT_DIR/mongod3.conf"

mkdir /tmp/mongodb
cp "$MONGO_CONF" /tmp/mongod.conf

MAX_CON=`ulimit -n`

if [ $MAX_CON -gt 1024 ]; then
    MAX_CON=`expr $MAX_CON - 1024`
fi

echo "  maxIncomingConnections: $MAX_CON" >> /tmp/mongod.conf

echo "# Configuration:"
cat /tmp/mongod.conf

# MongoDB startup

cat > /tmp/validate-env.sh <<EOF
PATH="$PATH"
LD_LIBRARY_PATH="$LD_LIBRARY_PATH"
EOF

MONGOD_CMD="mongod -f /tmp/mongod.conf --fork"

if [ `which numactl | wc -l` -gt 0 ]; then
    numactl --interleave=all $MONGOD_CMD
else
    $MONGOD_CMD
fi

MONGOD_ST="$?"

if [ ! $MONGOD_ST -eq 0 ]; then
    echo -e "\nERROR: Fails to start the custom 'mongod' instance" > /dev/stderr
    mongod --version
    PID=`ps -ao pid,comm | grep 'mongod$' | cut -d ' ' -f 1`

    if [ ! "x$PID" = "x" ]; then
        pid -p $PID
    fi

    tail -n 100 /tmp/mongod.log

    exit $MONGOD_ST
fi

# Check Mongo connection
PRIMARY_HOST="localhost:27017"
MONGOSHELL_OPTS="$PRIMARY_HOST/FOO"

MONGOSHELL_OPTS="$MONGOSHELL_OPTS --eval"
MONGODB_NAME=`mongo $MONGOSHELL_OPTS 'db.getName()' 2>/dev/null | tail -n 1`

if [ ! "x$MONGODB_NAME" = "xFOO" ]; then
    echo -n "\nERROR: Fails to connect using the MongoShell\n"
    mongo $MONGOSHELL_OPTS 'db.getName()'
    tail -n 100 /tmp/mongod.log
    exit 2
fi
