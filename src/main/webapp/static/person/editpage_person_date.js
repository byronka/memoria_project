"use strict";

/**
 * This class is for controlling the date field (birth and death)
 * on a person.
 */
class PersonDate {

    addListener = () => {
        // if the user selects "unknown", we'll disable the input for
        // the date.  This is purely a UI/UX consideration - in reality
        // when we send back the form data, if the server sees the
        // checkbox set for unknown, we'll ignore whatever value is in
        // the date.
        document.querySelectorAll('.unknown-date-checkbox').forEach(
            x => x.addEventListener(
                'change',
                event => {
                    const dateInput = event.target.closest('.date_section').querySelector('.date-input');
                    const yearOnlyCheckbox = event.target.closest('.date_section').querySelector('.year-only-checkbox');
                    dateInput.disabled = event.target.checked;
                    yearOnlyCheckbox.disabled = event.target.checked;
        }));

        // if the user selects "year-only", we'll replace the input for
        // date with a numeric input that can take a reasonable value for a
        // year, and will send back to the server that the year-only checkbox
        // is set, which will cause it to look at the incoming date value as
        // being year-only.
        document.querySelectorAll('.year-only-checkbox').forEach(
            x => x.addEventListener(
                'change',
                event => {
                    const dateInput = event.target.closest('.date_section').querySelector('input');
                    const yearPartOfValue = dateInput.value.slice(0,4);
                    const bornordied = event.target.closest('.date_section').getAttribute('data-bornordied');
                    // if they just checked the box, we need to replace the input with type="number"
                    if (event.target.checked) {
                        const yearOnlyInput = document.createElement('input');
                        yearOnlyInput.setAttribute('type','number');
                        yearOnlyInput.setAttribute('name', bornordied+'_input');
                        yearOnlyInput.setAttribute('id', bornordied+'_input');
                        yearOnlyInput.setAttribute('class','date-input');
                        yearOnlyInput.setAttribute('value',yearPartOfValue);
                        dateInput.replaceWith(yearOnlyInput);
                    } else {
                    // if they unchecked the box, need to replace the input with type="date"
                        const regularDateInput = document.createElement('input');
                        regularDateInput.setAttribute('type','date');
                        regularDateInput.setAttribute('name',bornordied+'_input');
                        regularDateInput.setAttribute('id',bornordied+'_input');
                        regularDateInput.setAttribute('class','date-input');
                        regularDateInput.setAttribute('value',yearPartOfValue + '-01-01');
                        dateInput.replaceWith(regularDateInput);
                    }

        }));
    }
}


// run the code after the page is fully loaded
addEventListener("load", (event) => {
    const personDate = new PersonDate();
    personDate.addListener();
});