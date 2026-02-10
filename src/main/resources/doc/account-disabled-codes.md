# Account Disabled Codes

## 0 - Enabled

## 1 - Account Merge
Accounts were merged, and thus this one is disabled.

## 2 - Account Disconnected
Account was removed from all users, and thus is disabled.

## 3 - Key Authorization Failure
Public key could not be authorized, or doesn't match the one associated with the profile.

## 4 - Account Disabled
A generic disabled code.

May be set manually.

## 5 - Shared Account
Account was disabled as its UUID was leaked.

## 6 - Subnet Ban

## 7 - Graylisted

## 8 - Banned
User is banned from the server. Bans apply globally.

- `reason`: Reason for why the account is banned.
- `expires`: Unix timestamp before which the ban is valid. `null` if permanent.
- `admin`: Last username of an admin that issues a ban.
- `server`: Server name on which the ban was issued.

## 9 - Kicked
User is kicked from the server. Kicks apply per-server.

- `reason`: Reason for why the account is banned.
- `expires`: Unix timestamp before which the kick is valid. `null` if permanent.
- `admin`: Last username of an admin that issues a ban.

## 10 - Already Logged In
User is already logged in from a different location.

This code is usually not issued by the database.