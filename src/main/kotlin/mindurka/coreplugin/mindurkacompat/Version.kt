package mindurka.coreplugin.mindurkacompat

import mindurka.annotations.PublicAPI

/**
 * MindurkaCompat features.
 */
@PublicAPI
abstract class Version {
    /** A version for no version. */
    object None: Version() {
        override val available = false
        override val updateRequired = false
    }

    /**
     * Versions before any advanced features that clients could
     * interact with were added.
     */
    object Base: Version()

    /** Forts plots v2 format. */
    object V6: Version() {
        override val updateRequired = false
        override val setData = true
    }

    companion object {
        /**
         * Get feature list for MindurkaCompat version.
         */
        @PublicAPI
        fun of(version: Int): Version {
            if (version <= 0) return None
            if (version >= 6) return V6
            return Base
        }
    }

    /** Check if MindurkaCompat is installed. */
    open val available: Boolean = true

    /** Check if MindurkaCompat update is required. */
    open val updateRequired: Boolean = true

    /** Check if 'mindurka.setData' is supported. */
    open val setData: Boolean = false
}