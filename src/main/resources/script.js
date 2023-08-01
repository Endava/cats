let selectedFilter = "All";
let selectedFuzzer = "All";
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
	const filter = selectedFilter.toUpperCase();
	const fuzzer = selectedFuzzer.toUpperCase();

	document.querySelectorAll('#summaryTable tr:not(.header)').forEach((tr) => {
		const result = tr.querySelector(".test-result").textContent.toUpperCase();
		const fuzzerName = tr.querySelector("td:nth-child(2)").textContent.toUpperCase();
		const scenario = tr.querySelector(".scenario").textContent.toUpperCase();
		const searchText = document.querySelector('.search-input').value.trim().toUpperCase();

		const matchFilter = filter === "ALL" || result.includes(filter);
		const matchFuzzer = fuzzer === "ALL" || fuzzerName.includes(fuzzer);
		const matchSearch = searchText === "" || fuzzerName.includes(searchText) || result.includes(searchText) || scenario.includes(searchText);

		if (matchFilter && matchFuzzer && matchSearch) {
			tr.style.display = '';
		} else {
			tr.style.display = 'none';
		}
	});
}


function handleFilter(filter) {
	selectedFilter = filter;
	populateTable();
}

function handleSearch() {
	const searchInput = document.querySelector('.search-input');
	const searchText = searchInput.value.trim().toUpperCase();

	document.querySelectorAll('#summaryTable tr:not(.header)').forEach((tr) => {
		const anyMatchSearch = [...tr.children].some(td => td.textContent.toUpperCase().includes(searchText));
		const anyMatchFuzzer = [...tr.children].some(td => td.textContent.toUpperCase().includes(selectedFuzzer) || selectedFuzzer === "All");

		if (anyMatchSearch && anyMatchFuzzer) {
			tr.style.removeProperty('display');
			set.add(tr.querySelector("td:nth-child(2)").textContent);
		} else {
			tr.style.display = 'none';
		}
	});

	  const clearButton = document.querySelector('.search-clear-btn');
  	if (searchText.length > 0) {
    	clearButton.style.display = 'inline-block';
  	} else {
    	clearButton.style.display = 'none';
  }
}

function clearSearch() {
	const searchInput = document.querySelector('.search-input');
	searchInput.value = '';
	handleSearch();
}

document.addEventListener('DOMContentLoaded', function () {
	// Filter options
	document.querySelectorAll("#summary span, #summary span button").forEach(function (element) {
		element.addEventListener("click", function (e) {
			e.preventDefault();
			document.querySelector("#summary span.active").classList.remove("active");
			selectedFilter = element.getAttribute('data-filtered');
			element.classList.add("active");
			element.parentNode.classList.add("active");
			selectedFuzzer = "All";

			handleFilter(selectedFilter);
			set.clear();
			set.add("All");
		});
	});

	const searchInput = document.querySelector('.search-input');
	searchInput.addEventListener('input', handleSearch);
	handleSearch();
});

//theme
const themeToggleBtn = document.getElementById('theme-toggle');
const body = document.body;
const logoWhite = document.querySelector('.logo-white');
const logoDark = document.querySelector('.logo-dark');
const themeIconLight = document.querySelector('.theme-icon-light');
const themeIconDark = document.querySelector('.theme-icon-dark');

function enableDarkMode() {
  body.classList.add('dark-mode');
  logoWhite.style.display = 'none';
  logoDark.style.display = 'block';
  themeIconLight.style.display = 'block';
  themeIconDark.style.display = 'none';
}

function enableLightMode() {
  body.classList.remove('dark-mode');
  logoWhite.style.display = 'block';
  logoDark.style.display = 'none';
  themeIconLight.style.display = 'none';
  themeIconDark.style.display = 'block';
}

function toggleTheme() {
  if (body.classList.contains('dark-mode')) {
    enableLightMode();
  } else {
    enableDarkMode();
  }
}

const prefersDarkMode = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
if (prefersDarkMode) {
  enableDarkMode();
} else {
  enableLightMode();
}

// Show the button after the initial rendering
themeToggleBtn.style.display = 'block';
themeToggleBtn.addEventListener('click', toggleTheme);

