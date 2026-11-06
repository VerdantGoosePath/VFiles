# VFiles Rust archive engine

This Android library replaces the previous libarchive-based archive reader and writer.

| Format | Rust crate | Read/extract | Create |
| --- | --- | --- | --- |
| ZIP | `rawzip` or `ripzip` (`ripzip-rs`) | Yes | Yes |
| 7z | `sevenz-turbo` | Yes | Yes |
| RAR | `unrar-rs` | Yes | No: this crate is extraction-oriented |
| TAR, TAR.GZ, TAR.XZ | `tar`, `flate2`, `lzma-rust2` | Yes | Yes |

ZIP implementation is selected globally in VFiles settings, and can be overridden
for an individual compression operation. rawzip supports legacy ZipCrypto passwords for compatibility;
ripzip-rs remains unencrypted. Prefer AES-encrypted 7z for sensitive data. Encrypted 7z is supported, including optional file-name
encryption. Other formats previously read by libarchive need separate format
adapters rather than silently falling back to an obsolete native backend.

## Building

Install Rust, cargo-ndk, and Android NDK 28.1.13356709, then install Rust
targets `armv7-linux-androideabi`, `aarch64-linux-android`,
`i686-linux-android`, and `x86_64-linux-android`.

`./gradlew :app:assembleDebug` invokes `cargo ndk` automatically. The
`app/src/main/jniLibs` directory receives per-ABI `libvfiles_archive.so`.
Host tests: `cargo test --manifest-path rust/Cargo.toml`.

## Integration details

The existing Android `java8.nio.file.Path` abstraction supports root,
documents providers, network shares, and local files. Rust's `std::fs` is not
a drop-in replacement for those providers. The Kotlin bridge therefore stages
archive inputs and user selections inside the application's private cache, then
transfers results through the original provider streams. Individual archive
members are extracted into temporary cache files and deleted when streams close.

The archive entry reader rejects absolute paths, parent traversals, NULs,
backslashes, and Windows drive prefixes. Rust applies per-entry size limits, and
ripzip bulk extraction additionally checks the aggregate temporary footprint
before extraction. Solid 7z skips must still consume earlier members to decode
later files.

## Known limitations

- Remote archive access is not seek-streamed across JNI. Each archive
  inspection or entry open currently stages the complete source archive.
- ZIP WinZip AES encryption and formats outside the table require further Rust work.
- RAR checksum verification and maximum extraction size are configurable.
- Unsupported symlinks are rejected when compressing staged selections,
  rather than silently following them.
- Existing timestamp/owner metadata are not yet fully carried into Rust's
  format-neutral entry list; restore if required before release.

Do not merge without Rust tests, Android builds, and manual tests of protected,
multi-volume, malformed, and remote archives.
