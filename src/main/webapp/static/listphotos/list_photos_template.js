 class ListPhotosFunctions {

    addEventHandlers = () => {
        // add a click event to every delete button on the page
        document.querySelectorAll('.delete_button').forEach(
            x => x.addEventListener(
                'click',
                // this event will get the photo's id, and send a DELETE request
                // to the server.
                async event => {

                    // if the user has JavaScript enabled, we'll use this instead of the
                    // default form action
                    event.preventDefault();

                    // get the photograph's id off the "photoid" attribute on the button
                    const photoid = event.target.dataset.photoid

                    // get the photoRow of the photo to delete, to operate on later.
                    const photoRow = document.querySelector(`tr[data-photoid="${photoid}"]`);

                    // build a request
                    const request = new Request("photo?id=" + photoid, {
                      method: "DELETE",
                    });

                    try {
                        // send the request, then delete that row
                        await fetch(request);
                        photoRow.remove();
                    } catch (error) {
                        console.error(`deletion error: ${error.message}`);
                        photoRow.classList.add('failed-border')
                    }
                }
            )
        )

        // add a click event to every long description save button on the page
        document.querySelectorAll('.long_description_save_button').forEach(
            x => x.addEventListener(
                'click',
                // this event will get the photo's id, and send a PATCH request
                // to the server to update the long description
                async event => {
                    // need to prevent the default - which is to post a form and redirect the page
                    event.preventDefault();
                    // get the photograph's id off the "photoid" attribute on the button
                    const photoid = event.target.dataset.photoid
                    const descriptionText = document.querySelector('textarea.long_description[data-photoid="'+photoid+'"]')

                    // build a request
                    const request = new Request("photolongdescupdate?id=" + photoid, {
                      method: "PATCH",
                      headers: {
                        "Content-Type": "plain/text",
                      },
                      body: descriptionText.value
                    });

                    try {
                        // send the request, then reload the page
                        await fetch(request);
                        descriptionText.classList.add('all-good-border')
                        setTimeout(() => {
                            descriptionText.classList.remove('all-good-border')
                        }, 3000)
                    } catch (error) {
                        console.error(`save error: ${error.message}`);
                        descriptionText.classList.add('failed-border')
                        setTimeout(() => {
                            descriptionText.classList.remove('failed-border')
                            alert('Save failed');
                        }, 3000)
                    }
                }
            )
        )


        // add a click event to every short description save button on the page
        document.querySelectorAll('.short_description_save_button').forEach(
            x => x.addEventListener(
                'click',
                // this event will get the photo's id, and send a PATCH request
                // to the server to update the long description
                async event => {
                    // need to prevent the default - which is to post a form and redirect the page
                    event.preventDefault();
                    // get the photograph's id off the "photoid" attribute on the button
                    const photoid = event.target.dataset.photoid
                    const descriptionText = document.querySelector('textarea.short_description[data-photoid="'+photoid+'"]')

                    // build a request
                    const request = new Request("photocaptionupdate?id=" + photoid, {
                      method: "PATCH",
                      headers: {
                        "Content-Type": "plain/text",
                      },
                      body: descriptionText.value
                    });

                    try {
                        // send the request, then reload the page
                        await fetch(request);
                        descriptionText.classList.add('all-good-border')
                        setTimeout(() => {
                            descriptionText.classList.remove('all-good-border')
                        }, 3000)
                    } catch (error) {
                        console.error(`save error: ${error.message}`);
                        descriptionText.classList.add('failed-border')
                        setTimeout(() => {
                            descriptionText.classList.remove('failed-border')
                            alert('Save failed');
                        }, 3000)

                    }
                }
            )
        )
    }
}


addEventListener("load", (event) => {
    const listPhotosFunctions = new ListPhotosFunctions()
    listPhotosFunctions.addEventHandlers();
});