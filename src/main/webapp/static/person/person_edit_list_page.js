"use strict";

/***************************************
*        Delete buttons                *
***************************************/

class DeleteButtonsHandling {
    // every delete button on the page
    all_delete_buttons

    constructor() {
        this.all_delete_buttons = document.querySelectorAll('.delete_button');
    }

    // add a click event to every delete button on the page
    applyClickEvents = () => {

        this.all_delete_buttons.forEach(
            x => x.addEventListener(
                'click',

                // this event will get the person's id, and send a DELETE request
                // to the server.
                async event => {

                    // if the user has JavaScript enabled, we only want to use that,
                    // instead of letting the browser send a form action
                    event.preventDefault();

                    // get the person's id off the "personid" attribute on the button
                    const personid = event.target.getAttribute('data-personid')

                    // get the photoRow of the photo to delete, to operate on later.
                    const personDiv = document.getElementById(`${personid}_details`);

                    // build a request
                    const request = new Request("person?id=" + personid, {
                      method: "DELETE",
                    });

                    // send the request, then reload the page
                    try {
                        // send the request, then delete that row
                        await fetch(request);
                        personDiv.remove();
                    } catch (error) {
                        console.error(`deletion error: ${error.message}`);
                        personDiv.classList.add('failed-border')
                    }
                }
            )
        )
    }
}


addEventListener("load", (event) => {
    const deleteButtonsHandling = new DeleteButtonsHandling();
    deleteButtonsHandling.applyClickEvents();
});