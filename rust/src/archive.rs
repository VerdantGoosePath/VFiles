//! Archive formats used by the virtual filesystem and file jobs.
//! All extraction is single-entry into an app-private temporary file; archive
//! paths never become filesystem destinations. Resource limits apply to streams.
use std::{fs::{self, File}, io::{self, Read, Write}, path::{Path, PathBuf}};
use serde::{Serialize, Deserialize};
use walkdir::WalkDir;

type Result<T> = std::result::Result<T, Box<dyn std::error::Error>>;

#[derive(Debug, Deserialize)]
#[serde(default)]
pub struct Options {
    pub zip_engine: String,
    pub zip_method: String,
    pub level: u32,
    pub sevenz_solid: bool,
    pub sevenz_encrypt_headers: bool,
    pub rar_verify: bool,
    pub max_entry_bytes: u64,
}
impl Default for Options {
    fn default() -> Self {
        Self { zip_engine: "rawzip".into(), zip_method: "deflate".into(), level: 5,
            sevenz_solid: false, sevenz_encrypt_headers: true, rar_verify: true,
            max_entry_bytes: 2 * 1024 * 1024 * 1024 }
    }
}
#[derive(Debug, Serialize)]
pub struct Entry {
    pub name: String,
    pub size: u64,
    pub directory: bool,
    pub encrypted: bool,
    pub symlink: bool,
    pub modified_ms: Option<u64>,
    pub link_target: Option<String>,
}

fn invalid(message: impl Into<String>) -> io::Error {
    io::Error::new(io::ErrorKind::InvalidData, message.into())
}
fn check_name(name: &str) -> Result<()> {
    if name.is_empty() || name.starts_with('/') || name.starts_with('\\')
        || name.contains('\0') || name.contains('\\')
        || name.split('/').any(|component| component == "..")
        || name.as_bytes().get(1) == Some(&b':') {
        return Err(invalid(format!("unsafe archive entry path: {name}")).into());
    }
    Ok(())
}
fn ensure_size(size: u64, options: &Options) -> Result<()> {
    if size > options.max_entry_bytes {
        return Err(invalid(format!("archive entry exceeds the configured {} byte extraction limit", options.max_entry_bytes)).into())
    }
    Ok(())
}
fn copy_limited<R: Read, W: Write>(mut reader: R, mut writer: W, options: &Options) -> Result<()> {
    let count = io::copy(&mut reader.by_ref().take(options.max_entry_bytes.saturating_add(1)), &mut writer)?;
    if count > options.max_entry_bytes { return Err(invalid("extraction size limit exceeded").into()); }
    Ok(())
}
fn format(path: &Path) -> Result<&'static str> {
    let name = path.file_name().and_then(|s| s.to_str()).unwrap_or("").to_lowercase();
    if name.ends_with(".zip") || name.ends_with(".jar") || name.ends_with(".apk") { Ok("zip") }
    else if name.ends_with(".7z") { Ok("7z") }
    else if name.ends_with(".rar") || name.ends_with(".cbr") { Ok("rar") }
    else if name.ends_with(".tar.xz") || name.ends_with(".txz") { Ok("tar.xz") }
    else if name.ends_with(".tar.gz") || name.ends_with(".tgz") { Ok("tar.gz") }
    else if name.ends_with(".tar") { Ok("tar") }
    else { Err(invalid(format!("unsupported archive format: {}", path.display())).into()) }
}

pub fn list(path: &Path, password: &str, options: &Options) -> Result<Vec<Entry>> {
    let entries = match format(path)? {
        "zip" if options.zip_engine == "ripzip" => list_ripzip(path)?,
        "zip" => list_rawzip(path)?,
        "7z" => {
            use sevenz_turbo::{ArchiveReader, Password};
            let pwd = if password.is_empty() { Password::empty() } else { Password::new(password) };
            let reader = ArchiveReader::open(path, pwd)?;
            reader.archive().files.iter().map(|e| Entry {
                name: e.name.clone(), size: e.size, directory: e.is_directory,
                encrypted: false, symlink: e.is_symlink(),
                modified_ms: if e.has_last_modified_date {
                    let time: std::time::SystemTime = e.last_modified_date().into();
                    time.duration_since(std::time::UNIX_EPOCH).ok()
                        .map(|d| d.as_millis().min(u128::from(u64::MAX)) as u64)
                } else { None },
                link_target: None,
            }).collect()
        }
        "rar" => {
            use unrar_rs::RarArchive;
            let file = File::open(path)?;
            let rar = if password.is_empty() { RarArchive::open(file)? } else {
                RarArchive::open_with_password(file, password)?
            };
            rar.entries().enumerate().map(|(i,e)| Entry {
                name: e.name, size: e.unpacked_size.unwrap_or(0),
                directory: e.is_directory, encrypted: rar.member_is_encrypted(i),
                symlink: false, modified_ms: None, link_target: None,
            }).collect()
        }
        "tar" | "tar.gz" | "tar.xz" => list_tar(path)?,
        _ => unreachable!()
    };
    // Never offer paths that could escape the virtual archive root.
    Ok(entries.into_iter().filter(|e| {
        check_name(&e.name).is_ok() &&
            e.link_target.as_deref().map(|target| check_name(target).is_ok()).unwrap_or(true)
    }).collect())
}

fn list_rawzip(path: &Path) -> Result<Vec<Entry>> {
    let mut buffer = vec![0; rawzip::RECOMMENDED_BUFFER_SIZE];
    let archive = rawzip::ZipArchive::from_file(File::open(path)?, &mut buffer)?;
    let mut iter = archive.entries(&mut buffer);
    let mut entries = Vec::new();
    while let Some(e) = iter.next_entry()? {
        let name = String::from_utf8_lossy(e.file_path().as_ref()).into_owned();
        entries.push(Entry { name, size: e.uncompressed_size_hint(), directory: e.is_dir(),
            encrypted: e.flags().is_encrypted(), symlink: false,
            modified_ms: None, link_target: None });
    }
    Ok(entries)
}
fn list_ripzip(path: &Path) -> Result<Vec<Entry>> {
    Ok(ripzip::list_archive(path)?.into_iter().map(|e| Entry {
        name: e.file_name, size: e.uncompressed_size, directory: e.is_dir,
        encrypted: false,
        symlink: ((e.external_attributes >> 16) & 0o170000) == 0o120000,
        modified_ms: None, link_target: None,
    }).collect())
}
fn tar_reader(path: &Path) -> Result<Box<dyn Read>> {
    let file = File::open(path)?;
    Ok(match format(path)? {
        "tar.xz" => Box::new(lzma_rust2::XzReader::new(file, false)),
        "tar.gz" => Box::new(flate2::read::GzDecoder::new(file)),
        _ => Box::new(file),
    })
}
fn list_tar(path: &Path) -> Result<Vec<Entry>> {
    let mut archive = tar::Archive::new(tar_reader(path)?);
    let mut output = Vec::new();
    for entry in archive.entries()? {
        let e = entry?;
        output.push(Entry {
            name: e.path()?.to_string_lossy().to_string(),
            size: e.size(), directory: e.header().entry_type().is_dir(),
            encrypted: false, symlink: e.header().entry_type().is_symlink(),
            modified_ms: e.header().mtime().ok().map(|seconds| seconds.saturating_mul(1000)),
            link_target: e.link_name()?.map(|link| link.to_string_lossy().into_owned()),
        });
    }
    Ok(output)
}

pub fn extract_one(path: &Path, name: &str, target: &Path, password: &str, options: &Options) -> Result<()> {
    check_name(name)?;
    // Validate the declared metadata before allocating or creating output.
    let entry = list(path, password, options)?.into_iter().find(|e| e.name == name)
        .ok_or_else(|| invalid(format!("no such archive member: {name}")))?;
    if entry.directory || entry.symlink {
        return Err(invalid("directories and symlinks cannot be opened as regular file data").into());
    }
    ensure_size(entry.size, options)?;
    let operation = match format(path)? {
        "zip" if options.zip_engine == "ripzip" => extract_ripzip(path, name, target, options),
        "zip" => extract_rawzip(path, name, target, options),
        "7z" => extract_sevenz(path, name, target, password, options),
        "rar" => extract_rar(path, name, target, password, options),
        "tar" | "tar.xz" | "tar.gz" => extract_tar(path, name, target, options),
        _ => unreachable!(),
    };
    if operation.is_err() { let _ = fs::remove_file(target); }
    operation
}
fn extract_rawzip(path: &Path, name: &str, target: &Path, options: &Options) -> Result<()> {
    use rawzip::CompressionMethod;
    let mut buffer = vec![0; rawzip::RECOMMENDED_BUFFER_SIZE];
    let archive = rawzip::ZipArchive::from_file(File::open(path)?, &mut buffer)?;
    let mut entries = archive.entries(&mut buffer);
    let mut selected = None;
    while let Some(e) = entries.next_entry()? {
        if String::from_utf8_lossy(e.file_path().as_ref()) == name {
            selected = Some((e.wayfinder(), e.compression_method(), e.flags().is_encrypted()));
            break;
        }
    }
    let (location, method, encrypted) = selected.ok_or_else(|| invalid("missing zip entry"))?;
    if encrypted { return Err(invalid("ZIP password decryption is not supported by this engine").into()); }
    drop(entries);
    let entry = archive.get_entry(location)?;
    let output = File::create(target)?;
    match method {
        CompressionMethod::STORE => copy_limited(entry.verifying_reader(entry.reader()), output, options),
        CompressionMethod::DEFLATE => copy_limited(
            entry.verifying_reader(flate2::read::DeflateDecoder::new(entry.reader())), output, options),
        _ => Err(invalid(format!("unsupported ZIP compression method: {method:?}")).into()),
    }
}
fn extract_ripzip(path: &Path, name: &str, target: &Path, options: &Options) -> Result<()> {
    // ripzip extracts a complete archive. Isolate it inside a temporary directory
    // and reject oversized or unsafe members before launching its parallel workers.
    let entries = list_ripzip(path)?;
    let mut total = 0u64;
    for e in &entries {
        check_name(&e.name)?;
        if e.symlink {
            return Err(invalid("ripzip does not safely support symlink archive entries").into());
        }
        ensure_size(e.size, options)?;
        total = total.checked_add(e.size).ok_or_else(|| invalid("archive size overflow"))?;
    }
    if total > options.max_entry_bytes.saturating_mul(4) {
        return Err(invalid("bulk ZIP extraction would exceed the allowed temporary storage").into());
    }
    let temporary = tempfile::tempdir()?;
    ripzip::extract_to_directory(path, temporary.path(), &ripzip::NoProgress)?;
    let file = temporary.path().join(name);
    let canonical = file.canonicalize()?;
    if !canonical.starts_with(temporary.path().canonicalize()?) || !canonical.is_file() {
        return Err(invalid("unsafe extracted ZIP member").into());
    }
    copy_limited(File::open(canonical)?, File::create(target)?, options)
}
fn extract_sevenz(path: &Path, name: &str, target: &Path, password: &str, options: &Options) -> Result<()> {
    use sevenz_turbo::{ArchiveReader, Password};
    let pwd = if password.is_empty() { Password::empty() } else { Password::new(password) };
    let mut reader = ArchiveReader::open(path, pwd)?;
    let mut found = false;
    let mut failure: Option<String> = None;
    reader.for_each_entries(|entry, input| {
        if entry.name == name {
            found = true;
            let output = File::create(target);
            match output.and_then(|out| copy_limited(input, out, options).map_err(|e| io::Error::other(e.to_string()))) {
                Ok(()) => {},
                Err(e) => failure = Some(e.to_string()),
            }
            // Do not decode unrelated members after the requested file.
            return Ok(false);
        }
        if !entry.is_directory {
            // Solid blocks require earlier entry data to be consumed before
            // advancing to the requested entry, even when it is discarded.
            if let Err(e) = copy_limited(input, io::sink(), options) {
                failure = Some(e.to_string());
                return Ok(false);
            }
        }
        Ok(true)
    })?;
    if let Some(err) = failure { return Err(invalid(err).into()); }
    if !found { return Err(invalid("missing 7z entry").into()); }
    Ok(())
}
fn extract_rar(path: &Path, name: &str, target: &Path, password: &str, options: &Options) -> Result<()> {
    use unrar_rs::RarArchive;
    let file = File::open(path)?;
    let mut rar = if password.is_empty() { RarArchive::open(file)? } else { RarArchive::open_with_password(file, password)? };
    rar.set_verify(options.rar_verify);
    let index = rar.entries().position(|e| e.name == name)
        .ok_or_else(|| invalid("missing RAR entry"))?;
    let mut input = rar.by_index(index)?;
    copy_limited(&mut input, File::create(target)?, options)
}
fn extract_tar(path: &Path, name: &str, target: &Path, options: &Options) -> Result<()> {
    let mut archive = tar::Archive::new(tar_reader(path)?);
    for entry in archive.entries()? {
        let mut e = entry?;
        if e.path()?.to_string_lossy() == name {
            return copy_limited(&mut e, File::create(target)?, options);
        }
    }
    Err(invalid("missing TAR entry").into())
}

fn members(source: &Path) -> Result<Vec<(PathBuf, String, bool)>> {
    let mut result = Vec::new();
    for entry in WalkDir::new(source).follow_links(false).min_depth(1) {
        let entry = entry?;
        // Do not follow or archive symlink targets accidentally.
        if entry.file_type().is_symlink() { continue; }
        let relative = entry.path().strip_prefix(source)?.to_string_lossy().replace('\\', "/");
        check_name(&relative)?;
        result.push((entry.path().to_path_buf(), relative, entry.file_type().is_dir()));
    }
    Ok(result)
}
pub fn create(source: &Path, target: &Path, format_name: &str, password: &str, options: &Options) -> Result<()> {
    match format_name {
        "zip" if options.zip_engine == "ripzip" => {
            if !password.is_empty() { return Err(invalid("ripzip does not support encrypted ZIP output").into()); }
            let method = if options.zip_method == "zstd" { ripzip::CompressionMethod::Zstd }
                else { ripzip::CompressionMethod::Deflate };
            ripzip::compress_directory(source, target, options.level.clamp(1,9), method, &ripzip::NoProgress)?;
        }
        "zip" => create_rawzip(source, target, password, options)?,
        "7z" => create_sevenz(source, target, password, options)?,
        "tar.xz" => {
            if !password.is_empty() { return Err(invalid("TAR/XZ encryption unsupported").into()); }
            let stream = lzma_rust2::XzWriter::new(File::create(target)?, lzma_rust2::XzOptions::with_preset(options.level.min(9)))?;
            let stream = write_tar(stream, source)?;
            stream.finish()?;
        }
        "tar.gz" => {
            let stream = flate2::write::GzEncoder::new(File::create(target)?, flate2::Compression::new(options.level.min(9)));
            write_tar(stream, source)?.finish()?;
        }
        "tar" => { write_tar(File::create(target)?, source)?; }
        "rar" => return Err(invalid("unrar-rs supports reading and extracting RAR, not creating RAR archives").into()),
        _ => return Err(invalid("unsupported archive output format").into())
    }
    Ok(())
}
fn write_tar<W: Write>(output: W, source: &Path) -> Result<W> {
    let mut tar = tar::Builder::new(output);
    for (path, name, directory) in members(source)? {
        if directory { tar.append_dir(&name, &path)?; }
        else { tar.append_path_with_name(&path, &name)?; }
    }
    Ok(tar.into_inner()?)
}
fn create_rawzip(source: &Path, target: &Path, password: &str, options: &Options) -> Result<()> {
    if !password.is_empty() { return Err(invalid("rawzip encryption is not enabled in this build; create the archive without a password").into()); }
    let mut archive = rawzip::ZipArchiveWriter::new(File::create(target)?);
    for (path, name, directory) in members(source)? {
        if directory { archive.new_dir(format!("{name}/")).create()?; continue; }
        let method = options.zip_method.as_str();
        if method == "store" {
            let (mut entry, config) = archive.new_file(name.as_str()).start()?;
            let mut writer = config.wrap(&mut entry);
            io::copy(&mut File::open(&path)?, &mut writer)?;
            let (_, descriptor) = writer.finish()?;
            entry.finish(descriptor)?;
        } else {
            let (mut entry, config) = archive.new_file(name.as_str())
                .compression_method(rawzip::CompressionMethod::DEFLATE).start()?;
            let encoder = flate2::write::DeflateEncoder::new(&mut entry, flate2::Compression::new(options.level.min(9)));
            let mut writer = config.wrap(encoder);
            io::copy(&mut File::open(&path)?, &mut writer)?;
            let (encoder, descriptor) = writer.finish()?;
            encoder.finish()?;
            entry.finish(descriptor)?;
        }
    }
    archive.finish()?;
    Ok(())
}
fn create_sevenz(source: &Path, target: &Path, password: &str, options: &Options) -> Result<()> {
    use sevenz_turbo::{ArchiveEntry, ArchiveWriter, Password, encoder_options::{Lzma2Options, AesEncoderOptions}};
    let mut writer = ArchiveWriter::create(target)?;
    let method = Lzma2Options::from_level(options.level.min(9));
    if password.is_empty() { writer.set_content_methods(vec![method.into()]); }
    else {
        writer.set_content_methods(vec![AesEncoderOptions::new(Password::new(password)).into(), method.into()]);
        writer.set_encrypt_header(options.sevenz_encrypt_headers);
    }
    if options.sevenz_solid {
        use sevenz_turbo::SourceReader;
        let mut entries = Vec::new();
        let mut readers: Vec<SourceReader<Box<dyn Read>>> = Vec::new();
        let mut bytes = 0u64;
        for (path, name, directory) in members(source)? {
            if directory {
                if !entries.is_empty() {
                    writer.push_archive_entries(std::mem::take(&mut entries), std::mem::take(&mut readers))?;
                    bytes = 0;
                }
                writer.push_archive_entry::<io::Empty>(ArchiveEntry::new_directory(&name), None)?;
            } else {
                let size = fs::metadata(&path)?.len();
                entries.push(ArchiveEntry::from_path(&path, name));
                readers.push(SourceReader::new(Box::new(File::open(path)?) as Box<dyn Read>));
                bytes = bytes.saturating_add(size);
                // Bound solid block memory and preserve random access across batches.
                if entries.len() >= 128 || bytes >= 32 * 1024 * 1024 {
                    writer.push_archive_entries(std::mem::take(&mut entries), std::mem::take(&mut readers))?;
                    bytes = 0;
                }
            }
        }
        if !entries.is_empty() { writer.push_archive_entries(entries, readers)?; }
    } else {
        for (path, name, directory) in members(source)? {
            if directory {
                writer.push_archive_entry::<io::Empty>(ArchiveEntry::new_directory(&name), None)?;
            } else {
                writer.push_archive_entry(ArchiveEntry::from_path(&path, name), Some(File::open(path)?))?;
            }
        }
    }
    writer.finish()?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    fn sample() -> Result<(tempfile::TempDir, PathBuf)> {
        let temp = tempfile::tempdir()?;
        let source = temp.path().join("source");
        fs::create_dir(&source)?;
        fs::write(source.join("hello.txt"), b"Rust archive backend round trip")?;
        fs::create_dir(source.join("nested"))?;
        fs::write(source.join("nested").join("item.bin"), [1u8, 2, 3, 4, 5])?;
        Ok((temp, source))
    }
    fn roundtrip(extension: &str, options: Options) -> Result<()> {
        let (temp, source) = sample()?;
        let archive = temp.path().join(format!("output.{extension}"));
        create(&source, &archive, extension, "", &options)?;
        let items = list(&archive, "", &options)?;
        assert!(items.iter().any(|x| x.name == "hello.txt" && !x.directory));
        assert!(items.iter().any(|x| x.name == "nested/item.bin" && x.size == 5));
        let output = temp.path().join("restored.txt");
        extract_one(&archive, "hello.txt", &output, "", &options)?;
        assert_eq!(fs::read(output)?, b"Rust archive backend round trip");
        Ok(())
    }
    #[test]
    fn rawzip_roundtrip() -> Result<()> { roundtrip("zip", Options::default()) }
    #[test]
    fn rawzip_store_roundtrip() -> Result<()> {
        roundtrip("zip", Options { zip_method: "store".into(), ..Options::default() })
    }
    #[test]
    fn ripzip_roundtrip() -> Result<()> {
        roundtrip("zip", Options { zip_engine: "ripzip".into(), ..Options::default() })
    }
    #[test]
    fn sevenz_roundtrip() -> Result<()> { roundtrip("7z", Options::default()) }
    #[test]
    fn sevenz_solid_roundtrip() -> Result<()> {
        roundtrip("7z", Options { sevenz_solid: true, ..Options::default() })
    }
    #[test]
    fn tar_xz_roundtrip() -> Result<()> { roundtrip("tar.xz", Options::default()) }
    #[test]
    fn sevenz_password_roundtrip() -> Result<()> {
        let (temp, source) = sample()?;
        let archive = temp.path().join("encrypted.7z");
        let options = Options::default();
        create(&source, &archive, "7z", "correct horse battery", &options)?;
        let listing = list(&archive, "correct horse battery", &options)?;
        assert!(listing.iter().any(|e| e.name == "hello.txt"));
        let target = temp.path().join("decrypted.txt");
        extract_one(&archive, "hello.txt", &target, "correct horse battery", &options)?;
        assert_eq!(fs::read(target)?, b"Rust archive backend round trip");
        Ok(())
    }
    #[test]
    fn enforce_extraction_size_limit() -> Result<()> {
        let (temp, source) = sample()?;
        let archive = temp.path().join("limited.zip");
        create(&source, &archive, "zip", "", &Options::default())?;
        let target = temp.path().join("too-large.txt");
        let options = Options { max_entry_bytes: 5, ..Options::default() };
        assert!(extract_one(&archive, "hello.txt", &target, "", &options).is_err());
        assert!(!target.exists(), "a rejected archive member must not leave partial output");
        Ok(())
    }

    #[test]
    fn tar_symlink_metadata_preserved() -> Result<()> {
        let temp = tempfile::tempdir()?;
        let path = temp.path().join("links.tar");
        let mut builder = tar::Builder::new(File::create(&path)?);
        let mut header = tar::Header::new_gnu();
        header.set_entry_type(tar::EntryType::Symlink);
        header.set_size(0);
        header.set_link_name("inside.txt")?;
        header.set_cksum();
        builder.append_data(&mut header, "link.txt", io::empty())?;
        builder.finish()?;
        let entries = list(&path, "", &Options::default())?;
        let link = entries.iter().find(|e| e.name == "link.txt").unwrap();
        assert!(link.symlink);
        assert_eq!(link.link_target.as_deref(), Some("inside.txt"));
        Ok(())
    }

    #[test]
    fn reject_unsafe_archive_paths() {
        for path in ["../escape", "/absolute", "folder/../escape", "C:/drive", "a\\b", ""] {
            assert!(check_name(path).is_err(), "accepted {path:?}");
        }
    }
}
