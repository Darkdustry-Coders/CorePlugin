package mindurka.coreplugin.database

object Sus {
    /** Minimum amount of sus to get graylisted. */
    const val minGraylist = 1000f
    /**
     * Minimum amount of sus to get blacklisted.
     *
     * Ideally another service should configure the firewall entries to deny any IP
     * for which the sus reached this value.
     */
    const val minBlacklist = 2000f

    /** Amount of sus to give for new account creation. */
    const val perNewAccount = 600f
    /**
     * Amount of sus to give for logging in with a new USID.
     *
     * This only applies to existing accounts with valid sessions.
     */
    const val perNewUsid = 100f
    /**
     * Amount of sus to give for logging into an existing session.
     */
    const val perNewLogin = 50f
}