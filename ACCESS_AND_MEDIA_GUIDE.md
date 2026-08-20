# Users, Permissions & Media Library — v3.1.0

## First launch after upgrading from v3.0.x

If the installation already has the old single administrator password, v3.1.0 asks for that
password once and converts it into a named Administrator account. The obsolete auth.properties
file is removed after successful migration.

If no previous administrator password exists, the application asks you to create the first
Administrator account.

## Users & Access

Open Settings > Users & Access as an Administrator.

Available actions:
- Add User
- Edit Access
- Reset Password
- Delete User
- Refresh
- View Audit Log

Edit Access allows a role template plus individual permission checkboxes. This supports scenarios
such as:
- Operations user: Operations Calendar + Routes
- Manager: Employee Information + Operations Calendar + Media Library
- API administrator: API Administration + API Usage
- Display user: no Settings privileges

## Media Library

Announcements:
Used for standard company slideshow media.

Employee Photos:
Used by birthday, anniversary, and Employee of the Month cards.

Employee Showcase:
General employee/team/event photos automatically included in Main Showcase rotation.

Files are imported into the application's data directory. Users no longer need to know or manually
maintain the storage path.

## Security notes

Passwords are not stored in plaintext. Each user has a unique random salt and a PBKDF2-HMAC-SHA256
derived password hash. API credentials remain in their separate credentials file.

Permissions are enforced both by Settings navigation and by service-layer checks on user/media
mutations.
