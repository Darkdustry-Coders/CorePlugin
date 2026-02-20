package mindurka.coreplugin.mindurkacompat

import mindurka.annotations.PublicAPI

/**
 * MindurkaCompat features.
 */
@PublicAPI
abstract class Version {
    /** A version for no version. */
    object None: Version() {
        override val available: Boolean get() = false
    }

    /**
     * Versions before any advanced features that clients could
     * interact with were added.
     */
    object Base: Version()

    companion object {
        /**
         * Get feature list for MindurkaCompat version.
         */
        @PublicAPI
        fun of(version: Int): Version {
            if (version <= 0) return None
            return Base
        }
    }

    /**
     * Check if MindurkaCompat is installed.
     */
     open val available: Boolean = true
}