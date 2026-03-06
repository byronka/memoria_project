"use strict";

/**
  * This class is designed to inspect the form data on the person edit page, and provide an alert
  * if the user tries leaving the page with unsaved changes.
  */
class CheckForChangesPersonEdit {

    initialFormState;
    myForm;
    saveButton;

    constructor () {
        this.initialFormState = '';
        this.myForm = document.getElementById("editperson-form");
        this.saveButton = document.getElementById("form_submit_button");
    }

    getFormState() {
        return new URLSearchParams(new FormData(this.myForm)).toString();
    }

    storeFormState() {
        this.initialFormState = this.getFormState();
    }

    /**
      * When the user decides to leave the page, we run a program to compare the
      * state of the form to how it was when they first arrived.  If there are any
      * changes, running "Event.preventDefault()" and returning true are all that
      * is needed to cause the browser to show an alert for whether the user is
      * sure they want to leave the page, given there are unsaved changes.
      */
    setupWarnBeforeLeave() {
        // when we first load the page, store all the data, so we can compare against it later
        this.storeFormState();

        // set up an event to fire when the user is leaving the page.  At that time, if there
        // are unsaved changes, show a prompt for whether the user is sure they want to leave the page.
        window.addEventListener("beforeunload", (event) => {
            if (this.initialFormState != this.getFormState()) {
                event.preventDefault();
                return true;
            }
        });

        this.myForm.addEventListener("submit", () => {
            this.storeFormState();
        });
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
        this.myForm.querySelectorAll('select').forEach(this.setupEventForInputChanges);
        this.setupEventForExtraFieldsSection();
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


    setupEventForExtraFieldsSection = () => {
        const extraFieldsList = document.getElementById('extra_fields_list');
        const doCheck = (event) => {
           if (this.initialFormState != this.getFormState()) {
               this.saveButton.classList.add("unsaved-changes");
               this.saveButton.disabled = false;
           } else {
               this.saveButton.classList.remove("unsaved-changes");
               this.saveButton.disabled = true;
           }
        };
        extraFieldsList.addEventListener("click", doCheck);
        extraFieldsList.addEventListener("focusout", doCheck);
        extraFieldsList.addEventListener("input", doCheck);
    };
}

// run the code after the page is fully loaded
addEventListener("load", (event) => {
    const checkForChanges = new CheckForChangesPersonEdit();
    checkForChanges.setupWarnBeforeLeave();
    checkForChanges.setupEventsInspectingForFormChanges();
});