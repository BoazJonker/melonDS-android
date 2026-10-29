# Signing key

`release.jks.enc` is the permanent key used to sign Spirit Tracks HD and Pokemon Unbound builds, encrypted with AES-256
(`openssl enc -aes-256-cbc -pbkdf2 -iter 600000`). It's unusable without the password.

The build workflow decrypts it with the `SIGNING_PASSWORD` repository secret. Keep a copy of that
password somewhere safe: without it, builds can't be signed with this key, and a new key would mean
uninstalling the app before installing the next update.
