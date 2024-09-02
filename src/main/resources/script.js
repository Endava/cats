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
	if (searchInput) {
		searchInput.addEventListener('input', handleSearch);
		handleSearch();
	}
});

//theme
const themeToggleBtn = document.getElementById('theme-toggle');
const body = document.body;
const logoWhite = document.querySelector('.logo-white');
const logoDark = document.querySelector('.logo-dark');
const themeIconLight = document.querySelector('.theme-icon-light');
const themeIconDark = document.querySelector('.theme-icon-dark');
const timeWhite = document.querySelector('.time-white');
const timeDark = document.querySelector('.time-dark');
const methodsWhite = document.querySelector('.methods-white');
const methodsDark = document.querySelector('.methods-dark');
const fuzzersWhite = document.querySelector('.fuzzers-white');
const fuzzersDark = document.querySelector('.fuzzers-dark');
const linkWhite = document.querySelector('.link-white');
const linkDark  = document.querySelector('.link-dark');

function enableDarkMode() {
	body.classList.add('dark-mode');
	myChart.data.datasets[0].borderColor = '#1A1A1A';
	myChart.update();

	hideElement(logoWhite);
	hideElement(themeIconDark);
	hideElement(timeWhite);
	hideElement(methodsWhite);
	hideElement(fuzzersWhite);
	hideElement(linkWhite);

	displayElement(logoDark);
	displayElement(themeIconLight);
	displayElement(timeDark);
	displayElement(methodsDark);
	displayElement(fuzzersDark);
	displayElement(linkDark);
}

function enableLightMode() {
	body.classList.remove('dark-mode');

	if (myChart) {
		myChart.data.datasets[0].borderColor = '#fff';
		myChart.update();
	}

	displayElement(logoWhite);
	displayElement(themeIconDark);
	displayElement(timeWhite);
	displayElement(methodsWhite);
	displayElement(fuzzersWhite);
	displayElement(linkWhite);

	hideElement(logoDark);
	hideElement(themeIconLight);
	hideElement(timeDark);
	hideElement(methodsDark);
	hideElement(fuzzersDark);
	hideElement(linkDark);
}

function displayElement(element) {
	if (element) {
		element.style.display = 'block';
	}
}

function hideElement(element) {
	if (element) {
		element.style.display = 'none';
	}
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


// Page scripts
window.onload = function () {
	showCode(1);
};

function showCode(tabIndex) {
	const codeAreas = document.querySelectorAll('.code-area');
	const tabs = document.querySelectorAll('.tab');
	codeAreas.forEach(codeArea => codeArea.classList.remove('active'));
	tabs.forEach(tab => tab.classList.remove('active'));

	const selectedCodeArea = document.getElementById('code-' + tabIndex);
	const selectedTab = document.querySelector('.tab:nth-child(' + tabIndex + ')');
	if (selectedTab) {
		selectedCodeArea.classList.add('active');
		selectedTab.classList.add('active');
	}
}

function copyTabs() {
	const activeTab = document.querySelector('.tab.active');
	const tabIndex = Array.from(activeTab.parentNode.children).indexOf(activeTab);

	copyCode('code-' + (tabIndex + 1), '.copy-button');
}

function copyResponse() {
	copyCode('code-response','.copy-button-response');
}

function copyCatsReplay() {
	copyCode('code-cats-replay','.copy-button-cats-replay');
}

function copyCode(codeAreaId, copyButtonSelector) {
	const codeArea = document.getElementById(codeAreaId);
	const codeText = codeArea.querySelector('code').innerText;

	const tempTextarea = document.createElement('textarea');
	tempTextarea.value = codeText;
	document.body.appendChild(tempTextarea);
	tempTextarea.select();
	document.execCommand('copy');
	document.body.removeChild(tempTextarea);

	const copyButton = document.querySelector(copyButtonSelector);
	copyButton.textContent = 'Copied';
	copyButton.classList.add("copied");
	setTimeout(() => {
		copyButton.textContent = 'Copy code';
		copyButton.classList.remove("copied");
	}, 2000);
}
