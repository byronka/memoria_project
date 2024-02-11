"use strict";

// JavaScript that comes in handy on the person_edit.html page

// this event will get the person's id, and send a DELETE request
// to the server.
const deleteHandler = event => {
    // get the person's id off the "personid" attribute on the button
    const personid = event.target.getAttribute('data-personid')

    // build a request
    const request = new Request("person?id=" + personid, {
      method: "DELETE",
    });

    // send the request, then redirect to home
    fetch(request).then(() => location.replace('/'))
}