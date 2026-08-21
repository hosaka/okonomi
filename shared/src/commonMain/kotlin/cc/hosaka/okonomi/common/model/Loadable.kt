package cc.hosaka.okonomi.common.model

/**
 * A value that a screen has to wait for. Screens render [Loading]
 * as empty or in-progress chrome and [Ok] with the actual content.
 */
sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>

    data class Ok<T>(
        val value: T,
    ) : Loadable<T>
}
