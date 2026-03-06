"use strict";

/**
 * This class is to provide event handling on the "Image url" input field
 * on a person edit page, so that the preview immediately shows up once
 * the URL value is changed.
 */
class HeadshotChanger {

    addListener = () => {
        document
            .getElementById('image_input')
            .addEventListener('input', event => {
                const previewImage = document.getElementById('preview_thumbnail_image_in_edit');
                previewImage.src = event.target.value;
            });
    }
}


// run the code after the page is fully loaded
addEventListener("load", (event) => {
    const headshotChanger = new HeadshotChanger();
    headshotChanger.addListener();
});