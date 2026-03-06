"use strict";

/**
  * This class serves just to add some scripting to the elements
  * for removing relations on the edit page, to show an "are you sure?"
  * confirmation before they can remove a relation.
  */
class EditPageRelationRemovalDoubleCheck {
    // these are the buttons that a user can click and remove a relation
    removeRelationButtons;

    constructor() {
        this.removeRelationButtons = document.querySelectorAll(".remove-relation-button");
    }

    addListeners() {
        // add a listener to each of the remove-relation buttons
        this.removeRelationButtons.forEach((rrb) => rrb.addEventListener("click", () => {

            const shouldDelete = confirm('Are you sure you want to remove this relation?');

            if (!shouldDelete) {
                  event.preventDefault();
            }
        }));

    }
}


// run the code after the page is fully loaded
addEventListener("load", (event) => {
    const editPageRelationRemovalDoubleCheck = new EditPageRelationRemovalDoubleCheck();
    editPageRelationRemovalDoubleCheck.addListeners();
});
