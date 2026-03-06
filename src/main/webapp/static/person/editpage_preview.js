"use strict";

class EditPagePreview {
    // the inputs for toggling whether to show the photo preview
    photoPreviewToggles;
    // the biography preview checkboxes
    showPreviewCheckboxes;
    // the biography textarea
    bio;

    constructor() {
        this.photoPreviewToggles = document.querySelectorAll(".photo_preview_window_toggle");
        this.elContent = document.querySelector("#photo_preview_window");
        this.showPreviewCheckboxes = document.querySelectorAll('.show-html-preview')
    }

    addListeners() {
        // add a listener to show the photos preview window
        this.photoPreviewToggles.forEach((toggle) => toggle.addEventListener("click", () => {
            const previewWindow = toggle.closest('.biography-utilities').querySelector('.photo-preview');
            previewWindow.classList.toggle("is-hidden");
        }));

        // add a listener to show the biography preview
        this.showPreviewCheckboxes.forEach((checkbox) => checkbox.addEventListener("click", (event) => {

            const bio = checkbox.closest('.bio-subsection').querySelector('.biography-input-area');

            if (event.target.checked) {
                // create a preview
                const preview = document.createElement('div')
                preview.setAttribute('id', 'persons');

                bio.insertAdjacentElement("afterEnd",preview)
                preview.innerHTML = bio.value

                // hide the text area
                bio.style.display = 'none'


            } else {
                // show the bio textarea again
                bio.style.display = 'block'
                // kill off the preview
                const preview = document.getElementById('persons')
                preview.remove()
            }
        }));
    }

}


// run the code after the page is fully loaded
addEventListener("load", (event) => {
    const editPagePreview = new EditPagePreview();
    editPagePreview.addListeners();
});
