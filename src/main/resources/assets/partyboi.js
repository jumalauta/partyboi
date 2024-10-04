const submitBtns = document.querySelectorAll('input[type="submit"]')

submitBtns.forEach(submitBtn => {
    submitBtn.addEventListener('click', () => {
        submitBtn.setAttribute('disabled', 'disabled')
        const progress = document.createElement('progress')
        submitBtn.after(progress)
        submitBtn.form.submit()
    })
})