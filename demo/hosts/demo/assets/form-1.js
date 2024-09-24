// form.js
const characters = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
const generateString = (length) => {
    let result = ''
    const charactersLength = characters.length
    for (let i = 0; i < length; i++) {
        result += characters.charAt(Math.floor(Math.random() * charactersLength))
    }

    return result;
}

const validateCaptcha = async (event) => {
    event.preventDefault();
    let request = {
        code: document.getElementById("inputCaptcha").value,
        key: document.getElementById("captchaKey").value
    }

    const response = await fetch('/module/forms-module/captcha/validate', {
        method: 'POST',
        body: JSON.stringify(request)
    })

    const validationResponse = await response.json()

    if (!validationResponse.valid) {
        alert("captcha code is not valid")
        event.preventDefault()
        return false
    } else {
        console.log(event.target)
        event.target.submit()
        return true
    }
}

const ajaxValidateCaptcha = async () => {
    let request = {
        code: document.getElementById("inputCaptcha").value,
        key: document.getElementById("captchaKey").value
    }

    const response = await fetch('/module/forms-module/captcha/validate', {
        method: 'POST',
        body: JSON.stringify(request)
    })

    const validationResponse = await response.json()

    if (!validationResponse.valid) {
        return false
    } else {
        return true
    }
}

document.addEventListener("DOMContentLoaded", () => {
    if (document.getElementById("reloadCaptcha")) {
        document.getElementById("reloadCaptcha").addEventListener("click", () => {
            let href = new URL(document.getElementById("captchaImg").src)
            let key = generateString(8)
            href.searchParams.set('key', key)

            document.getElementById("captchaKey").value = key
            document.getElementById("captchaImg").src = href.toString()
        })
    }

    if (document.getElementById("ajaxForm")) {
            document.getElementById("ajaxForm").addEventListener("submit", (event) => {
                event.preventDefault()
                console.log("send form via ajax")
                if (!ajaxValidateCaptcha()) {
                    alert("invalid captcha provided");
                    return false
                }
                var form = event.target;
                var formData = new FormData(form);
                fetch(form.action, {
                    method: "post",
                    body: formData
                }).then(res => res.json()).then(console.log);
                
                return false
            })
        document.getElementById("submit-btn-test").addEventListener("click", (event) => {
            event.preventDefault()
            
            if (ajaxValidateCaptcha()) {
                alert("invalid captcha provided");
                return false
            }
            
            var form = document.getElementById("ajaxForm")
            var formData = new FormData(form);
            fetch(form.action, {
                method: "post",
                body: formData
            }).then(res => res.json()).then(console.log);
            return false;
        })
    }

})
