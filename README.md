# MRM PG Manager

Android client for PasarGuard admin accounts.

## Features in this first version

- Login with a full PasarGuard dashboard URL, admin username and password.
- The entered dashboard path is normalized to the panel API base URL; custom ports are supported.
- JWT token is saved using Android encrypted preferences; the password is never stored.
- User list, search and traffic usage display.
- Persian/English string resources and light theme.

## Build APK without a computer

Push this repository to GitHub. Open **Actions**, select **Build Android APK**, then download the `MRM-PG-Manager-debug-apk` artifact.

## Notes

The application calls `POST /api/admin/token` and `GET /api/user/s`. Use HTTPS and an administrator account with user-read permission.
