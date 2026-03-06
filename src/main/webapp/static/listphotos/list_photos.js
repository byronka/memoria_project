"use strict";

/**
 * These are the dynamic capabilities on the photos page, such as
 * sending new text descriptions of photos and deleting photos
 */
 class ListPhotosFunctions {

    // The div that holds the photo table, used when we need to replace the table
    tableContainer;

    // the UUID of the person for this media
    personId;

    constructor() {
        this.tableContainer = document.getElementById('table_container');
        this.personId = document.getElementById('view-person-detail-link').dataset.personid;
    }

    /**
      * the photoId is a number representing the id of the photograph in
      * the database.  The parentTr is the row (TR element) that contains this data.
      */
    reloadPhotoRow = (photoId, parentTr) => {
        // reload the whole table in place
        fetch(`/photorow?photoid=${photoId}&personid=${this.personId}`)
            .then((response) => {
                // if we don't get a 2xx, move to error handling
                if (response && response.status && response.status < 200 || response.status > 299) {
                    return Promise.reject(response);
                } else {
                    // send the body of the response to the next stage
                    return response.text();
                }
            }).then((text) => {

                // put the received body directly into the table as a replacement row
                parentTr.innerHTML = text;
                // hook up the JavaScript events again for this new row
                this.addEventHandlers(parentTr);
            }).catch((response) => {
                console.error("Error: " + response)
                alert("Error.  See developer console");
            })
    }

    /**
      * the videoId is a number representing the id of the video in
      * the database.  The parentTr is the row (TR element) that contains this data.
      */
    reloadVideoRow = (videoId, parentTr) => {
        // reload the whole table in place
        fetch(`/videorow?videoid=${videoId}&personid=${this.personId}`)
            .then((response) => {
                // if we don't get a 2xx, move to error handling
                if (response && response.status && response.status < 200 || response.status > 299) {
                    return Promise.reject(response);
                } else {
                    // send the body of the response to the next stage
                    return response.text();
                }
            }).then((text) => {

                // put the received body directly into the table as a replacement row
                parentTr.innerHTML = text;
                // hook up the JavaScript events again for this new row
                this.addEventHandlers(parentTr);
            }).catch((response) => {
                console.error("Error: " + response)
                alert("Error.  See developer console");
            })
    }

    /**
      * this adds event handling to all the buttons in the table.  If a
      * parentElement value is provided, it will be the parent for
      * the selector, so we can scope down the request to a particular row
      * in a table, for example, instead of the whole table.
      */
    addEventHandlers = (parentElement) => {

        let outermostParentElement = document.querySelector('html')
        if (parentElement) {
            outermostParentElement = parentElement;
        }

        // add a click event to every delete button on the page
        outermostParentElement.querySelectorAll('.delete_button.photo').forEach(
            x => x.addEventListener(
                'click',
                // this event will get the photo's id, and send a DELETE request
                // to the server.
                async event => {

                    // if the user has JavaScript enabled, we'll use this instead of the
                    // default form action
                    event.preventDefault();

                    var shouldDelete = confirm('Are you sure you want to delete this photo?');
                    if (!shouldDelete) {
                        return;
                    }

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

        // add a click event to every video delete button on the page
        outermostParentElement.querySelectorAll('.delete_button.video').forEach(
            x => x.addEventListener(
                'click',
                // this event will get the video's id, and send a DELETE request
                // to the server.
                async event => {

                    // if the user has JavaScript enabled, we'll use this instead of the
                    // default form action
                    event.preventDefault();

                    var shouldDelete = confirm('Are you sure you want to delete this video?');
                    if (!shouldDelete) {
                        return;
                    }

                    // get the video's id off the "videoid" attribute on the button
                    const videoid = event.target.dataset.videoid

                    // get the row of the video to delete, to operate on later.
                    const videoRow = document.querySelector(`tr[data-videoid="${videoid}"]`);

                    // build a request
                    const request = new Request("video?id=" + videoid, {
                      method: "DELETE",
                    });

                    try {
                        // send the request, then delete that row
                        await fetch(request);
                        videoRow.remove();
                    } catch (error) {
                        console.error(`deletion error: ${error.message}`);
                        videoRow.classList.add('failed-border')
                    }
                }
            )
        )

        // add a click event to every description save button on the page
        outermostParentElement.querySelectorAll('.description_save_button.photo').forEach(
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
                    const shortDescriptionText = document.querySelector('textarea.short_description[data-photoid="'+photoid+'"]')
                    const parentTr = event.target.closest('tr');

                    // build a request
                    const formData  = new FormData();
                    formData.append('long_description', descriptionText.value);
                    formData.append('caption', shortDescriptionText.value);
                    formData.append('photoid', photoid);

                    const request = new Request("/photodescriptionupdate", {
                      method: "PATCH",
                      headers: {
                        "Content-Type": "application/x-www-form-urlencoded",
                      },
                      body: new URLSearchParams(formData)
                    });

                    try {
                        // send the request, then reload and replace the table immediately
                        await fetch(request);
                        this.reloadPhotoRow(photoid, parentTr);
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

        // VIDEO SECTION FOLLOWS

        // add a click event to every description save button on the page
        outermostParentElement.querySelectorAll('.description_save_button.video').forEach(
            x => x.addEventListener(
                'click',
                // this event will get the video's id, and send a PATCH request
                // to the server to update the long description
                async event => {
                    // need to prevent the default - which is to post a form and redirect the page
                    event.preventDefault();
                    // get the video's id off the "videoid" attribute on the button
                    const videoid = event.target.dataset.videoid
                    const descriptionText = document.querySelector('textarea.long_description[data-videoid="'+videoid+'"]')
                    const shortDescriptionText = document.querySelector('textarea.short_description[data-videoid="'+videoid+'"]')
                    const parentTr = event.target.closest('tr');

                    // build a request
                    const formData  = new FormData();
                    formData.append('long_description', descriptionText.value);
                    formData.append('caption', shortDescriptionText.value);
                    formData.append('videoid', videoid);

                    const request = new Request("/videodescriptionupdate", {
                      method: "PATCH",
                      headers: {
                        "Content-Type": "application/x-www-form-urlencoded",
                      },
                      body: new URLSearchParams(formData)
                    });

                    try {
                        // send the request, then reload and replace the table immediately
                        await fetch(request);
                        this.reloadVideoRow(videoid, parentTr);
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

        // add a click event to every poster save button on the page
        outermostParentElement.querySelectorAll('.poster_button').forEach(
            x => x.addEventListener(
                'click',
                // this event will get the video's id, and send a PATCH request
                // to the server to update the poster
                async event => {
                    // need to prevent the default - which is to post a form and redirect the page
                    event.preventDefault();
                    // get the video's id off the "videoid" attribute on the button
                    const videoid = event.target.dataset.videoid
                    const posterInput = document.querySelector('input[data-videoid="'+videoid+'"]')
                    const parentTr = event.target.closest('tr');

                    // build a request
                    const request = new Request("videoposterupdate?id=" + videoid, {
                      method: "PATCH",
                      headers: {
                        "Content-Type": "plain/text",
                      },
                      body: posterInput.value
                    });

                    try {
                        // send the request, then reload and replace the table immediately
                        await fetch(request);
                        this.reloadVideoRow(videoid, parentTr);
                    } catch (error) {
                        console.error(`save error: ${error.message}`);
                        posterInput.classList.add('failed-border')
                        setTimeout(() => {
                            posterInput.classList.remove('failed-border')
                            alert('Save failed');
                        }, 3000)

                    }
                }
            )
        )

        // see "check_for_changes_photo_page.js" to see the definition of CheckForChangesPhotoPage
        outermostParentElement.querySelectorAll('table form.enabled_through_javascript').forEach(myForm => {
              const checkForChanges = new CheckForChangesPhotoPage(myForm);
              checkForChanges.setupEventsInspectingForFormChanges();
          });

    }
}


addEventListener("load", (event) => {
    const listPhotosFunctions = new ListPhotosFunctions()
    listPhotosFunctions.addEventHandlers();
});