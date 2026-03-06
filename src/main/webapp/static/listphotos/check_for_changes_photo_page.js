"use strict";

/**
  * This class is designed to inspect the form data on the photos page, and provide an alert
  * if the user tries leaving the page with unsaved changes.
  */
class CheckForChangesPhotoPage {

    initialFormState;
    myForm;
    saveButton;

    /**
      * Takes a parameter of "myForm", which is the form element
      * to which we are adding events.
      */
    constructor (myForm) {
        this.myForm = myForm;
        this.storeFormState();
        this.saveButton = myForm.querySelector("button[type=submit]");
    }

    getFormState() {
        return new URLSearchParams(new FormData(this.myForm)).toString();
    }

    storeFormState() {
        this.initialFormState = this.getFormState();
    }

    /**
      * This program will set up events on all the inputs of the form. If
      * the user makes any changes, a comparison is made against the state
      * of the form when we first entered - if there are changes, we will
      * adjust the class of the "Save" button so we can show there are
      * now changes to save.  There will be CSS styling on the save button
      * so that the default style shows it disabled.
      */
    setupEventsInspectingForFormChanges() {
        // set up the save button as disabled to start.  By setting it in
        // javascript, it allows us to do more if javascript is disabled
        this.saveButton.classList.remove("unsaved-changes");
        this.saveButton.disabled = true;

        this.myForm.querySelectorAll('input').forEach(this.setupEventForInputChanges);
        this.myForm.querySelectorAll('textarea').forEach(this.setupEventForInputChanges);
    }

    /** set up an event to fire when the user changes any inputs.  At that time, if there
      * are unsaved changes, put "unsaved-changes" as a class on the Save button. Conversely,
      * if there are no unsaved changes, ensure that the "unsaved-changes" is not on the Save button.
      */
    setupEventForInputChanges = (input) => {
        input.addEventListener("input", (event) => {
           if (this.initialFormState != this.getFormState()) {
               this.saveButton.classList.add("unsaved-changes");
               this.saveButton.disabled = false;
           } else {
               this.saveButton.classList.remove("unsaved-changes");
               this.saveButton.disabled = true;
           }
        });
    };

}

// run the code after the page is fully loaded
addEventListener("load", (event) => {
    // for each form on the page, set up events.  On the photo page,
    // every row has at least one form for the description, and
    // on video rows has another form for setting posters
    document.querySelectorAll('table form.enabled_through_javascript').forEach(myForm => {
        const checkForChanges = new CheckForChangesPhotoPage(myForm);
        checkForChanges.setupEventsInspectingForFormChanges();
    });

});