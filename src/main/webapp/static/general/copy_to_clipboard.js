"use strict";

/*****************************************
*         Copy to clipboard              *
*****************************************/

function copyTextToClipboard(element, text) {
    navigator.clipboard.writeText(text).then(function() {
        createAlertMessage('copied content to clipboard', element)
        show();
        setTimeout(() => hide(), 500);
    }, function(err) {
        console.error('failed to copy');
    });
}

function createAlertMessage(value, element) {
    const alertDiv = document.createElement('div');
    alertDiv.setAttribute('id', 'alert_message');
    alertDiv.style.background = 'white';
    alertDiv.style.color = 'black';
    alertDiv.style.width = '100px';
    alertDiv.style.position = 'absolute';
    alertDiv.style.border = '4px solid black';
    const heightValue = -70 - element.getBoundingClientRect().height;
    alertDiv.style.top = heightValue + 'px';
    alertDiv.style.left = '-33px';
    alertDiv.style.visibility = 'hidden';
    const innerMessage = document.createElement('p');
    innerMessage.setAttribute('id', 'alert_message_content');
    innerMessage.textContent = value;
    alertDiv.appendChild(innerMessage);
    element.style.position = 'relative';
    element.appendChild(alertDiv);
}

function show() {
    const alertDiv = document.getElementById('alert_message')
	alertDiv.style.visibility = 'visible';
	alertDiv.style.opacity = '1';
	alertDiv.style.transition = 'opacity 0.5s linear';
}

function hide() {
    const alertDiv = document.getElementById('alert_message')
	alertDiv.style.visibility = 'hidden';
	alertDiv.style.opacity = '0';
	alertDiv.style.transition = 'visibility 0s 0.5s, opacity 0.5s linear';
	setTimeout(() => alertDiv.remove(), 500)
}