//! JNI interface for archive handling. Android providers stage non-local sources
//! to private cache files before calling this filesystem-only core.
mod archive;

use std::path::Path;
use std::panic::{catch_unwind, AssertUnwindSafe};
use jni::{JNIEnv, objects::{JObject, JString}, sys::jstring};

type Result<T> = std::result::Result<T, Box<dyn std::error::Error>>;

fn read(env: &mut JNIEnv, value: &JString) -> Result<String> {
    Ok(env.get_string(value)?.into())
}
fn options(value: &str) -> Result<archive::Options> {
    Ok(serde_json::from_str(value)?)
}
fn report(env: &mut JNIEnv, outcome: std::thread::Result<Result<()>>) {
    let error = match outcome {
        Ok(Ok(())) => return,
        Ok(Err(err)) => err.to_string(),
        Err(_) => "internal Rust archive panic".to_string(),
    };
    let _ = env.throw_new("java/io/IOException", error);
}

#[no_mangle]
pub extern "system" fn Java_me_zhanghai_android_files_provider_archive_archiver_RustArchiveBridge_listNative<'a>(
    mut env: JNIEnv<'a>, _this: JObject<'a>, archive_path: JString<'a>,
    password: JString<'a>, options_json: JString<'a>
) -> jstring {
    let result = catch_unwind(AssertUnwindSafe(|| -> Result<String> {
        let path = read(&mut env, &archive_path)?;
        let password = read(&mut env, &password)?;
        let opts = options(&read(&mut env, &options_json)?)?;
        Ok(serde_json::to_string(&archive::list(Path::new(&path), &password, &opts)?)?)
    }));
    match result {
        Ok(Ok(json)) => match env.new_string(json) {
            Ok(s) => s.into_raw(),
            Err(e) => { let _ = env.throw_new("java/io/IOException", e.to_string()); std::ptr::null_mut() }
        },
        other => {
            report(&mut env, other.map(|r| r.map(|_| ())));
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_me_zhanghai_android_files_provider_archive_archiver_RustArchiveBridge_extractNative<'a>(
    mut env: JNIEnv<'a>, _this: JObject<'a>, archive_path: JString<'a>,
    entry_name: JString<'a>, target: JString<'a>,
    password: JString<'a>, options_json: JString<'a>
) {
    let result = catch_unwind(AssertUnwindSafe(|| -> Result<()> {
        let path = read(&mut env, &archive_path)?;
        let entry = read(&mut env, &entry_name)?;
        let target = read(&mut env, &target)?;
        let password = read(&mut env, &password)?;
        let opts = options(&read(&mut env, &options_json)?)?;
        archive::extract_one(Path::new(&path), &entry, Path::new(&target), &password, &opts)
    }));
    report(&mut env, result);
}

#[no_mangle]
pub extern "system" fn Java_me_zhanghai_android_files_provider_archive_archiver_RustArchiveBridge_createNative<'a>(
    mut env: JNIEnv<'a>, _this: JObject<'a>, source: JString<'a>, target: JString<'a>,
    format: JString<'a>, password: JString<'a>, options_json: JString<'a>
) {
    let result = catch_unwind(AssertUnwindSafe(|| -> Result<()> {
        let source = read(&mut env, &source)?;
        let target = read(&mut env, &target)?;
        let format = read(&mut env, &format)?;
        let password = read(&mut env, &password)?;
        let opts = options(&read(&mut env, &options_json)?)?;
        archive::create(Path::new(&source), Path::new(&target), &format, &password, &opts)
    }));
    report(&mut env, result);
}
