"use strict";

/**
 * This class handles modifying the UI on the persons page
 * so that the user does not need to click the "sort" button
 * to sort.  He only needs to select an option in the dropdown
 */
class SortingBehavior {
    /**
     * The sort button - useful when the user lacks JavaScript,
     * but we *do* have JavaScript - so we'll just hide it.
     */
    sortButton;

    /**
     * The Select element for the different sortings that are
     * available - for example, birthdate, ascending.
     */
    sortSelect;

    constructor() {
        this.sortButton = document.getElementById('sort-submit')
        this.sortSelect = document.getElementById('sort-select')
    }

    /**
     * Adds handling for events on the page, hides unnecessary UI
     */
    addListener = () => {
        this.sortSelect.addEventListener('change', this.sortChangeHandler);
    }

    /**
     * This will see how the sort selector has changed and
     * take appropriate action - sorting and reloading the
     * page if necessary.
     */
    sortChangeHandler = (event) => {
        this.sortButton.click();
    }
}


// run the code after the page is fully loaded
addEventListener("load", (event) => {
    const sortingBehavior = new SortingBehavior();
    sortingBehavior.addListener();
});
