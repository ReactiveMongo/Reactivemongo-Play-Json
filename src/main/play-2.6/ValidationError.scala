package reactivemongo.play.json

/** Factory and extractor */
object ValidationError {
  import play.api.libs.json.{ JsonValidationError => VE }

  def apply(messages: Seq[String], args: Seq[Any]): VE =
    VE(messages, args: _*)

  def unapply(underlying: VE): Option[(Seq[String], Seq[Any])] =
    underlying match {
      case VE(messages, args @ _*) => Some(messages -> args)
      case _                       => None
    }
}
