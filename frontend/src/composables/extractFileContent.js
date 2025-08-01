const extractFileContent = async (file) => {

    let response = await file.text();

    return { response }
    
}

export default extractFileContent;
