
/*****************************************
*         Copy to clipboard              *
*****************************************/

function copyTextToClipboard(text) {
    navigator.clipboard.writeText(text).then(function() {
        console.log('copied: ' + text);
    }, function(err) {
        console.error('failed to copy');
    });
}