const ctx = document.getElementById('myChart').getContext('2d');
const legendContainer = document.getElementById('legend-container');

const initialColors = [
    'rgba(210, 0, 17, 0.8)', // #D20011
    'rgba(255, 201, 10, 0.8)', // #FFC90A
    'rgba(83, 173, 12, 0.8)'  // #53AD0C
];

const data = {
    labels: ['Errors ', 'Warns ', 'Success '],
    datasets: [{
        data: [totalErr, totalWrn, totalScs],
        backgroundColor: [...initialColors],
        hoverOffset: 4
    }]
};

const myChart = new Chart(ctx, {
    type: 'doughnut',
    data: data,
    options: {
        responsive: true,
        plugins: {
            legend: {
                display:false
            },
            tooltip: {
                callbacks: {
                    title: function() {
                        return '';
                    },
                    label: function(context) {
                        let label = context.label || '';
                        if (label) {
                            label += ': ';
                        }
                        label += context.raw;
                        return ' ' + label;
                    }
                }
            },
        },
        layout: {
            padding: 0
        }
    }
});

function formatNumberWithSpaces(number) {
    return number.toString().replace(/\B(?=(\d{3})+(?!\d))/g, " ");
}

// Custom legend
function createCustomLegend() {
    legendContainer.innerHTML = '';
    data.labels.forEach((label, index) => {
        const legendItem = document.createElement('div');
        legendItem.classList.add('legend-item');

        const colorLabelDiv = document.createElement('div');
        colorLabelDiv.classList.add('legend-color-label');

        const colorBox = document.createElement('span');
        colorBox.classList.add('color-box');
        colorBox.style.backgroundColor = data.datasets[0].backgroundColor[index];

        const labelSpan = document.createElement('span');
        labelSpan.classList.add('legend-label');
        labelSpan.textContent = label;

        const valueSpan = document.createElement('span');
        valueSpan.classList.add('legend-value');
        valueSpan.textContent = formatNumberWithSpaces(data.datasets[0].data[index]);

        colorLabelDiv.appendChild(colorBox);
        colorLabelDiv.appendChild(labelSpan);
        legendItem.appendChild(colorLabelDiv);
        legendItem.appendChild(valueSpan);
        legendContainer.appendChild(legendItem);
    });
}

createCustomLegend();
