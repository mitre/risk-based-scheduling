import axios from 'axios';

const performHttpRequest = async (config) => {

    let data 
    let status 


    try {
        let response = await axios(config);
        status = response.status
        if (response.status < 400) {
            data = response.data
        } else {
            throw Error('Oops, something went wrong.')
        }
    } catch (error) {
        data = 'Oops, something went wrong.'
        if (!status) {
            console.log(error)
            status = 500
        }
    } 

    return {'status': status, 'data': data}
}


export default performHttpRequest;