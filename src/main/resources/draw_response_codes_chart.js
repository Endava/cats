const responseCodeCtx = document.getElementById('responseCodeChart').getContext('2d');
const responseCodeLegendContainer = document.getElementById('response-code-legend-container');

const familyColors = {
    '2xx': 'rgba(83, 173, 12, 0.8)',   // Green for success
    '3xx': 'rgba(59, 130, 246, 0.8)',  // Blue for redirects
    '4xx': 'rgba(255, 201, 10, 0.8)',  // Yellow/Orange for client errors
    '5xx': 'rgba(210, 0, 17, 0.8)',    // Red for server errors
    'other': 'rgba(156, 163, 175, 0.8)' // Gray for other
};

const responseCodeLabels = responseCodeData.map(item => item.code.toString());
const responseCodeCounts = responseCodeData.map(item => item.count);
const responseCodeColors = responseCodeData.map(item => familyColors[item.family] || familyColors['other']);

const responseCodeChartData = {
    labels: responseCodeLabels,
    datasets: [{
        data: responseCodeCounts,
        backgroundColor: responseCodeColors,
        hoverOffset: 4
    }]
};

const responseCodeChart = new Chart(responseCodeCtx, {
    type: 'bar',
    data: responseCodeChartData,
    options: {
        responsive: true,
        plugins: {
            legend: {
                display: false
            },
            tooltip: {
                callbacks: {
                    title: function(context) {
                        return 'HTTP ' + context[0].label;
                    },
                    label: function(context) {
                        return ' Count: ' + context.raw;
                    }
                }
            }
        },
        scales: {
            y: {
                beginAtZero: true,
                ticks: {
                    precision: 0
                },
                title: {
                    display: true,
                    text: 'Count'
                }
            },
            x: {
                title: {
                    display: true,
                    text: 'Response Code'
                }
            }
        }
    }
});

function formatNumberWithSpacesRC(number) {
    return number.toString().replace(/\B(?=(\d{3})+(?!\d))/g, " ");
}

function createResponseCodeLegend() {
    responseCodeLegendContainer.innerHTML = '';
    
    const families = ['2xx', '3xx', '4xx', '5xx'];
    const familyLabels = {
        '2xx': 'Success (2xx)',
        '3xx': 'Redirect (3xx)',
        '4xx': 'Client Error (4xx)',
        '5xx': 'Server Error (5xx)'
    };
    
    const familyCounts = {};
    responseCodeData.forEach(item => {
        if (!familyCounts[item.family]) {
            familyCounts[item.family] = 0;
        }
        familyCounts[item.family] += item.count;
    });
    
    families.forEach(family => {
        if (familyCounts[family]) {
            const legendItem = document.createElement('div');
            legendItem.classList.add('legend-item');

            const colorLabelDiv = document.createElement('div');
            colorLabelDiv.classList.add('legend-color-label');

            const colorBox = document.createElement('span');
            colorBox.classList.add('color-box');
            colorBox.style.backgroundColor = familyColors[family];

            const labelSpan = document.createElement('span');
            labelSpan.classList.add('legend-label');
            labelSpan.textContent = familyLabels[family] + ' ';

            const valueSpan = document.createElement('span');
            valueSpan.classList.add('legend-value');
            valueSpan.textContent = formatNumberWithSpacesRC(familyCounts[family]);

            colorLabelDiv.appendChild(colorBox);
            colorLabelDiv.appendChild(labelSpan);
            legendItem.appendChild(colorLabelDiv);
            legendItem.appendChild(valueSpan);
            responseCodeLegendContainer.appendChild(legendItem);
        }
    });
}

createResponseCodeLegend();
