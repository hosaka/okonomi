package cc.hosaka.okonomi.user

/**
 * Where a swallowed user-data failure goes.
 *
 * Every failure path around `user.db` ends in a catch, deliberately: a
 * screen must not go down because storage did. The cost is that a store
 * which has quietly stopped persisting is byte-identical, to the reader
 * and to a bug report, to a store with nothing in it — on the only data
 * in this app that cannot be regenerated. So nothing is caught silently;
 * everything caught is reported here.
 *
 * `println` rather than a logging framework, following the one precedent
 * this project already has (the iOS dictionary provisioning reports a
 * failed backup exclusion the same way). It is replaceable in tests,
 * which is the other reason it is a function rather than a bare call.
 */
internal typealias UserDataFailureReporter = (String, Throwable?) -> Unit

internal val printUserDataFailure: UserDataFailureReporter = { message, cause ->
    println("okonomi: $message${cause?.let { ": $it" }.orEmpty()}")
}
