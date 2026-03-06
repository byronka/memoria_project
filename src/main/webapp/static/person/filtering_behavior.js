"use strict";

/**
 * This class handles modifying the UI on the persons page
 * so that the user does not need to click the "filter" button
 * to filter.  He only needs to select an option in the dropdown
 */
class FilteringBehavior {
    /**
     * The filter button - useful when the user lacks JavaScript,
     * but we *do* have JavaScript - so we'll just hide it.
     */
    filterButton;

    /**
     * The Select element for the different filterings that are
     * available - for example, birthdate, ascending.
     */
    filterSelect;

    constructor() {
        this.filterButton = document.getElementById('filter-submit')
        this.filterSelect = document.getElementById('filter-select')
    }

    /**
     * Adds handling for events on the page, hides unnecessary UI
     */
    addListener = () => {
        this.filterSelect.addEventListener('change', this.filterChangeHandler);
    }

    /**
     * This will see how the filter selector has changed and
     * take appropriate action - filtering and reloading the
     * page if necessary.
     */
    filterChangeHandler = (event) => {
        this.filterButton.click();
    }
}


// run the code after the page is fully loaded
addEventListener("load", (event) => {
    const filteringBehavior = new FilteringBehavior();
    filteringBehavior.addListener();
});
