window.alert = function () {
};

let selectedFilter = "ALL";
let selectedFuzzer = "ALL";
let set = new Set();

const entityMap = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;',
    '/': '&#x2F;',
    '`': '&#x60;',
    '=': '&#x3D;'
};

function escapeHtml(string) {
    return String(string).replace(/[&<>"'`=\/]/g, function (s) {
        return entityMap[s];
    });
}

function populateTable() {
    const filter = String(selectedFilter).toUpperCase();
    const fuzzer = String(selectedFuzzer).toUpperCase();


    document.querySelectorAll('#summaryTable tr:not(.header)').forEach((tr) => {
        const anyMatchFilter = [...tr.children].some(td => td.textContent.toUpperCase().includes(filter) || filter === "ALL");
        const anyMatchFuzzer = [...tr.children].some(td => td.textContent.toUpperCase().includes(fuzzer) || fuzzer === "ALL");

        if (anyMatchFilter && anyMatchFuzzer) {
            tr.style.removeProperty('display');
            set.add($(tr).find("td:eq(1)").text());
        } else {
            tr.style.display = 'none';
        }
    });

}

$(document).ready(function () {
    selectedFuzzer = $("#fuzzerSelect option:selected").text();
    populateTable();

    //filter options
    $("#summary span, #summary span b").on("click", function (e) {
        e.preventDefault();
        $('#summary span.active').removeClass("active");
        selectedFilter = $(e.target).attr('data-filtered');
        $(e.target).addClass("active");
        $(e.target).parent("span").addClass("active");
        selectedFuzzer = "ALL";

        populateTable();
        set.clear();
        set.add("All");
        $('#summaryTable tr:visible').each(function () {
            set.add($(this).find("td:eq(1)").text());
        });
        $('#fuzzerSelect').empty();
        let select = document.getElementById("fuzzerSelect");

        for (let item of set) {
            if (item) {
                let el = document.createElement("option");
                el.text = item;
                el.value = item;

                select.add(el);
            }
        }
        $("#fuzzerSelect").val("All");
    })
//
//dropdown filter options
    $("#summary .menu-filter").on("change", function (e) {
        e.preventDefault();

        selectedFuzzer = this.value;

        populateTable();
        $("#fuzzerSelect").val(selectedFuzzer);
    });

})
;
