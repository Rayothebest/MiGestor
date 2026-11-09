(() => {
    const colors = ['#6354d6', '#2b8a78', '#e59544', '#dd6c80', '#5084c7', '#9a71c1',
        '#58a075', '#b78d3c', '#687a9a', '#b65f9b', '#4bacae', '#9b9e51'];
    const format = new Intl.NumberFormat('es-MX', { maximumFractionDigits: 4 });
    const percentFormat = new Intl.NumberFormat('es-MX', { maximumFractionDigits: 1 });

    const element = (tag, className, text) => {
        const node = document.createElement(tag);
        if (className) node.className = className;
        if (text !== undefined) node.textContent = text;
        return node;
    };

    const renderBars = (plot, points) => {
        const max = Math.max(1, ...points.map((point) => Math.abs(point.value)));
        const chart = element('div', 'saved-chart-bars');
        points.forEach((point) => {
            const row = element('div', 'saved-chart-bar-row');
            const label = element('span', 'saved-chart-bar-label', point.label);
            label.title = point.label;
            const track = element('span', 'saved-chart-bar-track');
            const fill = element('span', `saved-chart-bar-fill${point.value < 0 ? ' negative' : ''}`);
            fill.style.width = `${Math.max(0, Math.abs(point.value) / max * 100)}%`;
            track.append(fill);
            row.append(label, track, element('strong', 'saved-chart-bar-value', format.format(point.value)));
            chart.append(row);
        });
        plot.append(chart);
    };

    const renderPie = (plot, points) => {
        const total = points.reduce((sum, point) => sum + point.value, 0);
        if (total <= 0) return renderBars(plot, points);
        const wrapper = element('div', 'saved-chart-pie-layout');
        const pie = element('div', 'saved-chart-pie');
        const legend = element('div', 'saved-chart-legend');
        let start = 0;
        const slices = [];
        points.forEach((point, index) => {
            const end = start + point.value / total * 100;
            slices.push(`${colors[index % colors.length]} ${start}% ${end}%`);
            start = end;
            const item = element('div', 'saved-chart-legend-item');
            const swatch = element('span', 'saved-chart-swatch');
            swatch.style.backgroundColor = colors[index % colors.length];
            item.append(swatch, element('span', '', point.label),
                element('strong', '', `${format.format(point.value)} (${percentFormat.format(point.value / total * 100)}%)`));
            legend.append(item);
        });
        pie.style.background = `conic-gradient(${slices.join(',')})`;
        wrapper.append(pie, legend);
        plot.append(wrapper);
    };

    const renderLine = (plot, points) => {
        const svgNS = 'http://www.w3.org/2000/svg';
        const svg = document.createElementNS(svgNS, 'svg');
        svg.setAttribute('viewBox', '0 0 700 245');
        svg.setAttribute('class', 'saved-chart-line');
        svg.setAttribute('aria-hidden', 'true');
        const min = Math.min(0, ...points.map((point) => point.value));
        const max = Math.max(0, ...points.map((point) => point.value));
        const span = max - min || 1;
        const x = (index) => points.length === 1 ? 350 : 44 + index / (points.length - 1) * 620;
        const y = (value) => 204 - (value - min) / span * 168;
        const axis = document.createElementNS(svgNS, 'line');
        axis.setAttribute('x1', '44'); axis.setAttribute('x2', '664');
        axis.setAttribute('y1', String(y(0))); axis.setAttribute('y2', String(y(0)));
        axis.setAttribute('class', 'saved-chart-zero');
        svg.append(axis);
        const path = document.createElementNS(svgNS, 'polyline');
        path.setAttribute('points', points.map((point, index) => `${x(index)},${y(point.value)}`).join(' '));
        path.setAttribute('class', 'saved-chart-line-path');
        svg.append(path);
        points.forEach((point, index) => {
            const circle = document.createElementNS(svgNS, 'circle');
            circle.setAttribute('cx', String(x(index)));
            circle.setAttribute('cy', String(y(point.value)));
            circle.setAttribute('r', '5');
            circle.setAttribute('class', 'saved-chart-line-dot');
            const title = document.createElementNS(svgNS, 'title');
            title.textContent = `${point.label}: ${format.format(point.value)}`;
            circle.append(title);
            svg.append(circle);
            if (index === 0 || index === points.length - 1 || points.length <= 6 || index % 2 === 0) {
                const label = document.createElementNS(svgNS, 'text');
                label.setAttribute('x', String(x(index)));
                label.setAttribute('y', '231');
                label.setAttribute('text-anchor', 'middle');
                label.setAttribute('class', 'saved-chart-line-label');
                label.textContent = point.label.length > 10 ? `${point.label.slice(0, 9)}…` : point.label;
                svg.append(label);
            }
        });
        const maxLabel = document.createElementNS(svgNS, 'text');
        maxLabel.setAttribute('x', '5'); maxLabel.setAttribute('y', '38');
        maxLabel.setAttribute('class', 'saved-chart-axis-label');
        maxLabel.textContent = format.format(max);
        const minLabel = document.createElementNS(svgNS, 'text');
        minLabel.setAttribute('x', '5'); minLabel.setAttribute('y', '205');
        minLabel.setAttribute('class', 'saved-chart-axis-label');
        minLabel.textContent = format.format(min);
        svg.append(maxLabel, minLabel);
        plot.append(svg);
    };

    document.querySelectorAll('[data-report-chart]').forEach((container) => {
        const plot = container.querySelector('[data-chart-plot]');
        const points = [...container.querySelectorAll('[data-chart-point]')].map((row) => ({
            label: row.dataset.label || 'Sin valor', value: Number(row.dataset.value)
        }));
        if (!plot || !points.length || points.some((point) => !Number.isFinite(point.value))) return;
        const type = container.dataset.chartType;
        if (type === 'PIE') renderPie(plot, points);
        else if (type === 'LINE') renderLine(plot, points);
        else renderBars(plot, points);
    });
})();
