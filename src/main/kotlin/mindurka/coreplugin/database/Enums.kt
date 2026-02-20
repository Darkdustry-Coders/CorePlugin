package mindurka.coreplugin.database

/**
 * Account disabled codes.
 *
 * See 'resources/doc/account-disabled-codes.md' for documentation.
 */
object DisableCodes {
    /**
     * Account is enabled.
     *
     * System should proceed as normal.
     */
    const val enabled = 0
    /** Account is merged with another account. */
    const val merged = 1
    /** Account was removed from all users and thus is disabled. */
    const val disconnected = 2
    /** Failed to authorize the key, or a key was not provided for a secure account. */
    const val keyValidationFailure = 3
    /** Account was disabled by staff. */
    const val disabled = 4
    /** UUID of the account was leaked, and thus it was banned. */
    const val shared = 5
    /** The IP with which the user has attempted to log in was subnet banned. */
    const val subnetBan = 6
    /** The IP with which the user has attempted to log in was graylisted and thus requires extra verification. */
    const val graylisted = 7
    /**
     * The user was banned on the server.
     *
     * ## Parameters
     * - `id`: The ID of this ban.
     * - `reason`: Reason for why the account is banned.
     * - `expires`: Unix timestamp before which the ban is valid. `null` if permanent.
     * - `admin`: Last username of an admin that issues a ban.
     * - `server`: Server name on which the ban was issued.
     */
    const val banned = 8
    /**
     * The user was kicked from the server. Kicks are server-bound.
     *
     * ## Parameters
     * - `id`: The ID of this kick.
     * - `reason`: Reason for why the account is kicked.
     * - `expires`: Unix timestamp before which the ban is valid. `null` if permanent.
     * - `admin`: Last username of an admin that issues a ban.
     */
    const val kicked = 9
    /** The user is already logged in on this or another server. */
    const val alreadyLoggedIn = 10
    /**
     * The user was kicked from the server. Kicks are server-bound.
     *
     * ## Parameters
     * - `id`: The ID of this kick.
     * - `reason`: Reason for why the account is kicked.
     * - `expires`: Unix timestamp before which the ban is valid. `null` if permanent.
     * - `initiator`: Last username of a player that started the votekick.
     * - `votes`: Last usernames of players that voted in favor of the kick.
     */
    const val votekicked = 11
}