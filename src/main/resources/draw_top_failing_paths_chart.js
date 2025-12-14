const topFailingPathsCtx = document.getElementById('topFailingPathsChart').getContext('2d');
const topFailingPathsLegendContainer = document.getElementById('top-failing-paths-legend-container');

const topFailingPathsLabels = topFailingPathsData.map(item => item.path);
const topFailingPathsCounts = topFailingPathsData.map(item => item.count);

const topFailingPathsChartData = {
    labels: topFailingPathsLabels,
    datasets: [{
        data: topFailingPathsCounts,
        backgroundColor: 'rgba(210, 0, 17, 0.8)',
        hoverOffset: 4
    }]
};

const topFailingPathsChart = new Chart(topFailingPathsCtx, {
    type: 'bar',
    data: topFailingPathsChartData,
    options: {
        indexAxis: 'y',
        responsive: true,
        plugins: {
            legend: {
                display: false
            },
            tooltip: {
                callbacks: {
                    title: function(context) {
                        return context[0].label;
                    },
                    label: function(context) {
                        return ' Errors: ' + context.raw;
                    }
                }
            }
        },
        scales: {
            x: {
                beginAtZero: true,
                ticks: {
                    precision: 0
                },
                title: {
                    display: true,
                    text: 'Error Count'
                }
            },
            y: {
                ticks: {
                    callback: function(value, index) {
                        const label = this.getLabelForValue(value);
                        if (label.length > 30) {
                            return label.substring(0, 27) + '...';
                        }
                        return label;
                    }
                }
            }
        }
    }
});

function formatNumberWithSpacesTFP(number) {
    return number.toString().replace(/\B(?=(\d{3})+(?!\d))/g, " ");
}

function createTopFailingPathsLegend() {
    topFailingPathsLegendContainer.innerHTML = '';
    
    const totalErrors = topFailingPathsData.reduce((sum, item) => sum + item.count, 0);
    
    const legendItem = document.createElement('div');
    legendItem.classList.add('legend-item');

    const colorLabelDiv = document.createElement('div');
    colorLabelDiv.classList.add('legend-color-label');

    const colorBox = document.createElement('span');
    colorBox.classList.add('color-box');
    colorBox.style.backgroundColor = 'rgba(210, 0, 17, 0.8)';

    const labelSpan = document.createElement('span');
    labelSpan.classList.add('legend-label');
    labelSpan.textContent = 'Total Errors (Top 10) ';

    const valueSpan = document.createElement('span');
    valueSpan.classList.add('legend-value');
    valueSpan.textContent = formatNumberWithSpacesTFP(totalErrors);

    colorLabelDiv.appendChild(colorBox);
    colorLabelDiv.appendChild(labelSpan);
    legendItem.appendChild(colorLabelDiv);
    legendItem.appendChild(valueSpan);
    topFailingPathsLegendContainer.appendChild(legendItem);
}

createTopFailingPathsLegend();
