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

document.addEventListener("DOMContentLoaded", () => {
    if (document.getElementById("reloadCaptcha")) {
        document.getElementById("reloadCaptcha").addEventListener("click", () => {
            let href = new URL(document.getElementById("captchaImg").src)
            let key = crypto.randomUUID().replaceAll('-', '') + crypto.randomUUID().replaceAll('-', '')
            href.searchParams.set('key', key)

            document.getElementById("captchaKey").value = key
            document.getElementById("captchaImg").src = href.toString()
        })
    }

    if (document.getElementById("ajaxForm")) {
            document.getElementById("ajaxForm").addEventListener("submit", (event) => {
                event.preventDefault()
                var form = event.target;
                var formData = new URLSearchParams(new FormData(form));
                fetch(form.action, {
                    method: "post",
                    headers: {"Content-Type": "application/x-www-form-urlencoded"},
                    body: formData
                }).then(res => res.json()).then(result => {
                    if (!result.success) {
                        alert(result.code || "The form could not be submitted")
                    }
                });
                
                return false
            })
    }

})
