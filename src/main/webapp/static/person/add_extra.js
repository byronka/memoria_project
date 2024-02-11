"use strict";

/**
 * This class provides ability to add "extra fields" to a person.  These
 * store optional data - like wedding dates, occupations, etc.
 */
class AddExtra {

    /**
     * The button a user clicks to cause inputs to show
     * for adding optional fields.
     */
    addExtraButton;

    /**
     * This is the container for new extra fields
     */
    extraFieldsList;

    /**
     * The index of which extra field we are at.  Also usable as a
     * count of extra fields.  This is used to make the names and id's
     * of each field unique.  For context, each set of inputs should
     * be distinguishable from others.  If we have, for example,
     * two inputs or selects with the same id or name, that's broken.
     */
    extraFieldIndex;

    /**
     * This is where we store the current count of extra fields
     * for later processing on the server
     */
    extraFieldCountInput;

    /**
     * This is the input where we store the id's of current extra fields
     */
    extraFieldArrayInput;

    /**
     * This contains the actual identifiers of each extra field,
     * which we will send back to the server, so it knows which
     * to parse.
     */
    extraFieldIdSet;

    constructor() {
        this.addExtraButton = document.getElementById('add_extra_field_button')
        this.extraFieldsList = document.getElementById('extra_fields_list')
        this.extraFieldCountInput = document.getElementById('extra_field_count')
        this.extraFieldIndex = Number(this.extraFieldCountInput.value);

        // get the existing field id's when we start.
        this.extraFieldArrayInput = document.getElementById('extra_field_array')
        this.extraFieldIdSet = new Set(this.extraFieldArrayInput.value.split(',').map(Number))
    }

    /**
     * Put a click listener on the button to add extra fields, so all this code runs.
     */
    addExtraFieldCreateListener = () => {
        this.addExtraButton.addEventListener("click", this.extraFieldButtonHandler);
    }

    /**
      * handler for when a user clicks to create new optional fields.
      */
    extraFieldButtonHandler = (e) => {

        e.preventDefault(true);

        // increment our count of extra fields
        this.extraFieldIndex += 1;

        // add to our list of id's
        this.extraFieldIdSet.add(this.extraFieldIndex)
        // put the comma-separated value into the input element
        this.extraFieldArrayInput.value = [...this.extraFieldIdSet].join(',')

        // make a copy of the template which will be adjusted for uniqueness
        const extraFieldTemplate = document.getElementById('extra_field_template');
        const newExtraField = extraFieldTemplate.cloneNode(true);

        // make the new html unique - unique "name" per input, unique id's
        const labels = newExtraField.content.querySelectorAll('label')
        const inputs = newExtraField.content.querySelectorAll('input')
        const selects = newExtraField.content.querySelectorAll('select')
        const keyInput = newExtraField.content.querySelector('select.extra_data_key')
        const yearOnlyInput = newExtraField.content.querySelector('div.year_only_for_extra_value > input')
        const extraDataType = newExtraField.content.querySelector('select.extra_data_type')
        const listItem = newExtraField.content.querySelector('li.extra_field_item')
        const deleteButton = newExtraField.content.querySelector('button.extra_data_item_delete')

        // modify the label elements
        for (let index = 0; index < labels.length; index++) {
            labels[index].setAttribute('for', labels[index].getAttribute('for') + '_' + this.extraFieldIndex)
        }

        // modify the input elements - append a unique value
        for (let index = 0; index < inputs.length; index++) {
            inputs[index].setAttribute('id',   inputs[index].getAttribute('id')   + '_' + this.extraFieldIndex)
            inputs[index].setAttribute('name', inputs[index].getAttribute('name') + '_' + this.extraFieldIndex)
        }

        // modify the select elements - append a unique value
        for (let index = 0; index < selects.length; index++) {
            selects[index].setAttribute('id',   selects[index].getAttribute('id')   + '_' + this.extraFieldIndex)
            selects[index].setAttribute('name', selects[index].getAttribute('name') + '_' + this.extraFieldIndex)
        }

        // set up some events for changes
        keyInput.addEventListener("change", this.valueKeyChangeHandler);
        yearOnlyInput.addEventListener("change", this.yearOnlyCheckboxHandler);

        // modify the listItem element (the container of each extra field)
        listItem.setAttribute('id',   listItem.getAttribute('id')  + '_' + this.extraFieldIndex)

        // add an event listener to the delete button per extra field
        // (the x in the top right)
        deleteButton.addEventListener('click', this.deleteButtonClickHandler)

        // finally, put our modified template into its container
        this.extraFieldsList.appendChild(newExtraField.content)

        // put the current count of extra fields into an input so we can
        // determine how many to read during processing on the server.
        this.extraFieldCountInput.value = this.extraFieldIndex
    }

    /**
     * When the user clicks the button to delete an "extra field item",
     * it is removed entirely.
     */
    deleteButtonClickHandler = (e) => {
        e.preventDefault();

        // find the containing element for this delete button
        const containingListItem = e.target.closest('li.extra_field_item')

        // get the index of this extra field
        const index = Number(containingListItem.id.replace('extra_data_item_',''))

        // remove this item's id from our list of id's
        this.extraFieldIdSet.delete(index)

        // put the comma-separated value into the input element
        this.extraFieldArrayInput.value = [...this.extraFieldIdSet].join(',')

        // delete this extra field
        containingListItem.remove()

    };

    /**
     * The point of this event handler is so when a user select a key for the extra
     * field, if it is one we recognize (e.g. anniversary) we can automatically set
     * the type for them, just as a courtesy.  They are still able to adjust this
     * manually, but it can save clicks.
     *
     * (Note: we currently hide the type field - we set this fully automatically for
     * the user)
     */
    valueKeyChangeHandler = (e) => {
        const myValue = e.target.value;
        switch (myValue) {
            case 'Wedding date':
            case 'Graduation date':
            case 'Immigration date':
                this.setType(e.target, 'date');
                break;
            case 'Birthplace':
            case 'Deathplace':
            case 'Occupation':
                this.setType(e.target, 'string');
                break;
            default:
                break;
        }
    }

    yearOnlyCheckboxHandler = (e) => {
        const yearOnlyCheckbox = e.target;
        const parentListItemId = yearOnlyCheckbox.closest('li.extra_field_item').getAttribute('id');
        const selectForType = document.querySelector(`li#${parentListItemId} .extra_data_type select`);
        const inputForValue = document.querySelector(`li#${parentListItemId} > .extra_data_value > input`)
        const extraDataValueElement = yearOnlyCheckbox.closest('li.extra_field_item').querySelector('div.extra_data_value input');
        if (yearOnlyCheckbox.checked) {
            selectForType.value = 'number'
            inputForValue.setAttribute('type', 'number')
        } else {
            selectForType.value = 'date'
            inputForValue.setAttribute('type', 'date')
        }
    }

    /**
     * set the select field for this extra field to a particular type,
     * and show the checkbox for year-only if it is a date type.
     *
     * This code should properly work to set the data type, even if there are multiple
     * extra fields being set.  It does this by careful analysis of the identifiers and
     * classes in the template.
     *
     * @param keyElementInput: the input field we are using to set the name of the extra field's key
     * @param dataType: the type of data to set for this extra field, i.e. "date", "string", or "number"
     */
    setType = (keyElementInput, dataType) => {
        const parentListItemId = keyElementInput.closest('li.extra_field_item').getAttribute('id');
        const selectForType = document.querySelector(`li#${parentListItemId} .extra_data_type select`);
        const yearOnlySection = document.querySelector(`li#${parentListItemId} > .input_section.year_only_for_extra_value`);
        const yearOnlyCheckbox = document.querySelector(`li#${parentListItemId} > .input_section.year_only_for_extra_value > input`);
        selectForType.value = dataType
        yearOnlyCheckbox.checked = false;
        if (dataType === 'date') {
            yearOnlySection.style.display = 'block'
        } else {
            yearOnlySection.style.display = 'none'
            yearOnlyCheckbox.checked = false;
        }
        // find the elements we need to work with
        const inputForValue = document.querySelector(`li#${parentListItemId} > .extra_data_value > input`)

        inputForValue.setAttribute('type', selectForType.value)
    }

}

// run the code after the page is fully loaded
addEventListener("load", (event) => {
    const addExtra = new AddExtra();
    addExtra.addExtraFieldCreateListener();

    // add the delete handler
    const externalDeleteButtons = document.querySelectorAll('button.extra_data_item_delete')
    for(let i = 0; i < externalDeleteButtons.length; i++) {
       externalDeleteButtons[i].addEventListener("click", addExtra.deleteButtonClickHandler);
    }
});
