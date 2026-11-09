(() => {
    const movementForm = document.querySelector('[data-movement-form]');
    const kind = movementForm?.querySelector('[data-movement-kind]');
    const movementButton = movementForm?.querySelector('[data-movement-submit]');
    const syncKind = () => {
        if (movementButton && kind) movementButton.textContent = kind.value === 'INCOME' ? 'Guardar ingreso' : 'Guardar gasto';
    };
    kind?.addEventListener('change', syncKind);
    syncKind();

    const counted = document.querySelector('[data-counted-cash]');
    const preview = document.querySelector('[data-count-preview]');
    const difference = preview?.querySelector('[data-count-difference]');
    const cents = (value) => {
        const match = String(value ?? '').trim().match(/^(-?)(\d+)(?:\.(\d{1,2}))?$/);
        if (!match) return null;
        const number = BigInt(match[2]) * 100n + BigInt((match[3] || '').padEnd(2, '0'));
        return match[1] ? -number : number;
    };
    const money = (value) => {
        const sign = value < 0n ? '−' : value > 0n ? '+' : '';
        const absolute = value < 0n ? -value : value;
        return `${sign}${absolute / 100n}.${String(absolute % 100n).padStart(2, '0')}`;
    };
    const syncCount = () => {
        if (!preview || !difference || !counted) return;
        const actual = cents(counted.value);
        const expected = cents(preview.dataset.expectedCash);
        preview.classList.remove('is-short', 'is-over');
        if (actual === null || expected === null) {
            difference.textContent = '—';
            return;
        }
        const value = actual - expected;
        difference.textContent = money(value);
        if (value < 0n) preview.classList.add('is-short');
        if (value > 0n) preview.classList.add('is-over');
    };
    counted?.addEventListener('input', syncCount);
    syncCount();

    document.querySelectorAll('[data-cash-form]').forEach((form) => {
        form.addEventListener('submit', () => {
            const button = form.querySelector('button[type="submit"]');
            if (button) button.disabled = true;
        });
    });
})();
