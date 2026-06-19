use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::jint;
use std::path::Path;
use anyhow::{Context, Result, anyhow};
use std::fs;

#[no_mangle]
pub extern "system" fn Java_tech_bananajuice_convertpixelart_RustCore_convertFile(
    mut env: JNIEnv,
    _class: JClass,
    input_path: JString,
    output_path: JString,
    timelapse: jni::sys::jboolean,
) -> jint {
    let input_path_str: String = env.get_string(&input_path).expect("Couldn't get java string!").into();
    let output_path_str: String = env.get_string(&output_path).expect("Couldn't get java string!").into();

    match convert_impl(&input_path_str, &output_path_str, timelapse) {
        Ok(_) => 0,
        Err(e) => {
            eprintln!("Error converting file: {:?}", e);
            1
        }
    }
}

fn convert_impl(input_path_str: &str, output_path_str: &str, timelapse: jni::sys::jboolean) -> Result<()> {
    let input_path = Path::new(input_path_str);
    let output_path = Path::new(output_path_str);

    let doc = if input_path.is_file()
        && input_path
            .extension()
            .and_then(|e| e.to_str())
            .is_some_and(|ext| ext.eq_ignore_ascii_case("psp"))
    {
        handle_psp_format(&input_path, timelapse)?
    } else if input_path.is_file()
        && input_path
            .extension()
            .and_then(|e| e.to_str())
            .is_some_and(|ext| ext.eq_ignore_ascii_case("psd"))
    {
        handle_psd_format(&input_path)?
    } else if input_path.is_file()
        && input_path
            .extension()
            .and_then(|e| e.to_str())
            .is_some_and(|ext| {
                ext.eq_ignore_ascii_case("ase") || ext.eq_ignore_ascii_case("aseprite")
            })
    {
        handle_aseprite_format(&input_path)?
    } else if input_path.join("document.json").exists() {
        handle_modern_format(&input_path)?
    } else if input_path.join("DocumentInfo.plist").exists() {
        handle_legacy_format(&input_path)?
    } else if input_path.is_dir() {
        // Handle unzipped pixaki directory logic if necessary or just document.json
        if input_path.join("document.json").exists() {
            handle_modern_format(&input_path)?
        } else if input_path.join("DocumentInfo.plist").exists() {
            handle_legacy_format(&input_path)?
        } else {
             return Err(anyhow!("No valid document.json/DocumentInfo.plist found in the directory"));
        }
    } else {
        return Err(anyhow!(
            "No valid .psp, .psd, .ase, .aseprite file, or document.json/DocumentInfo.plist found in the given path"
        ));
    };

    let ext = output_path
        .extension()
        .and_then(|e| e.to_str())
        .unwrap_or("")
        .to_lowercase();

    if ext == "ase" || ext == "aseprite" {
        let aseprite_file = aseprite_converter::convert(doc)?;

        let mut buffer = Vec::new();
        aseprite_file
            .write_to(&mut buffer)
            .map_err(|e| anyhow!("Failed to write Aseprite file: {}", e))?;
        fs::write(&output_path, buffer)?;
    } else if ext == "png" {
        let img = doc.render();
        img.save(&output_path)
            .context("Failed to write PNG file")?;
    } else {
        return Err(anyhow!(
            "Unsupported output format: '{}'. Supported formats are .ase, .aseprite, and .png",
            ext
        ));
    }

    Ok(())
}

fn handle_modern_format(pixaki_path: &Path) -> Result<pixel_art::Document> {
    let document_path = pixaki_path.join("document.json");
    let json_str = fs::read_to_string(document_path)?;
    let doc_v3: pixaki_v3::Document =
        serde_json::from_str(&json_str).context("Unable to parse document.json")?;

    pixaki_v3_converter::convert(doc_v3, pixaki_path)
}

fn handle_legacy_format(pixaki_path: &Path) -> Result<pixel_art::Document> {
    let plist_path = pixaki_path.join("DocumentInfo.plist");
    let doc_v2: pixaki_v2::Document =
        plist::from_file(plist_path).context("Failed to parse DocumentInfo.plist")?;

    pixaki_v2_converter::convert(doc_v2, pixaki_path)
}

fn handle_psp_format(psp_path: &Path, timelapse: jni::sys::jboolean) -> Result<pixel_art::Document> {
    let json_str = fs::read_to_string(psp_path)?;
    let doc_psp: pixel_studio_pro_v2::Document =
        serde_json::from_str(&json_str).context("Unable to parse .psp JSON document")?;

    pixel_studio_pro_v2_converter::convert(doc_psp, timelapse != 0)
}

fn handle_psd_format(psd_path: &Path) -> Result<pixel_art::Document> {
    let bytes = fs::read(psd_path)?;
    psd_converter::convert(&bytes).context("Failed to parse .psd file")
}

fn handle_aseprite_format(ase_path: &Path) -> Result<pixel_art::Document> {
    let file = fs::File::open(ase_path)?;
    let aseprite_file =
        aseprite::AsepriteFile::from_reader(file).context("Failed to parse .aseprite file")?;
    aseprite_converter::reader::parse(aseprite_file)
}
