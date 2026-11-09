document.querySelectorAll('[data-open]').forEach((button) => {
    button.addEventListener('click', () => document.getElementById(button.dataset.open)?.showModal());
});

document.querySelectorAll('[data-close]').forEach((button) => {
    button.addEventListener('click', () => button.closest('dialog')?.close());
});

document.querySelectorAll('[data-confirm]').forEach((button) => {
    button.addEventListener('click', (event) => {
        if (!confirm(button.dataset.confirm)) event.preventDefault();
    });
});

document.querySelectorAll('[data-type-select]').forEach((select) => {
    const sync = () => {
        const form = select.closest('form');
        const field = form.querySelector('.options-field');
        if (field) field.hidden = select.value !== 'SELECT';
        form.querySelectorAll('[data-config-section]').forEach((section) => {
            section.hidden = section.dataset.configSection !== select.value;
        });
        const common = form.querySelector('[data-config-common]');
        if (common) common.hidden = ['LOOKUP', 'FORMULA'].includes(select.value);
    };
    select.addEventListener('change', sync);
    sync();
});

document.querySelectorAll('[data-module-form]').forEach((form) => {
    const typeInputs = form.querySelectorAll('[data-module-type]');
    const linkedField = form.querySelector('[data-linked-module-field]');
    const linkedSelect = form.querySelector('[data-linked-module-select]');
    const panelActionInputs = form.querySelectorAll('[data-panel-action]');

    const syncModuleType = () => {
        const selected = form.querySelector('[data-module-type]:checked');
        const isPanel = selected?.value === 'PANEL';
        if (linkedField) linkedField.hidden = !isPanel;
        if (linkedSelect) {
            linkedSelect.disabled = !isPanel;
            linkedSelect.required = isPanel;
        }
        panelActionInputs.forEach((input) => {
            input.disabled = !isPanel;
            input.required = isPanel;
        });
    };

    typeInputs.forEach((input) => input.addEventListener('change', syncModuleType));
    syncModuleType();
});

document.querySelectorAll('[data-column-config]').forEach((form) => {
    const relatedTable = form.querySelector('[data-related-module]');
    const relatedDisplay = form.querySelector('[data-related-display]');
    const lookupRelation = form.querySelector('[data-lookup-relation]');
    const lookupSource = form.querySelector('[data-lookup-source]');

    const filterOptions = (select, attribute, selectedValue) => {
        if (!select) return;
        Array.from(select.options).forEach((option, index) => {
            if (index === 0) return;
            option.hidden = option.dataset[attribute] !== String(selectedValue || '');
        });
        if (select.selectedOptions[0]?.hidden) select.value = '';
    };
    const syncRelation = () => filterOptions(relatedDisplay, 'relatedTable', relatedTable?.value);
    const syncLookup = () => filterOptions(lookupSource, 'relationColumn', lookupRelation?.value);
    relatedTable?.addEventListener('change', syncRelation);
    lookupRelation?.addEventListener('change', syncLookup);
    syncRelation();
    syncLookup();
});

document.querySelectorAll('[data-panel-settings]').forEach((form) => {
    const table = form.querySelector('[data-panel-table]');
    const operation = form.querySelector('[data-panel-operation]');
    const settings = form.querySelector('[data-adjust-settings]');
    const quantityColumn = form.querySelector('[data-adjustment-column]');
    const sync = () => {
        const adjusts = operation?.value === 'ADJUST';
        if (settings) settings.hidden = !adjusts;
        if (quantityColumn) {
            quantityColumn.required = adjusts;
            Array.from(quantityColumn.options).forEach((option, index) => {
                if (index === 0) return;
                option.hidden = option.dataset.adjustmentTable !== String(table?.value || '');
            });
            if (quantityColumn.selectedOptions[0]?.hidden) quantityColumn.value = '';
        }
    };
    table?.addEventListener('change', sync);
    operation?.addEventListener('change', sync);
    sync();
});

(() => {
    const moduleId = document.body.dataset.moduleId;
    const toggles = document.querySelectorAll('[data-column-visibility]');
    if (!moduleId || !toggles.length) return;
    const key = `migestor-visible-columns-${moduleId}`;
    let hidden = [];
    try { hidden = JSON.parse(localStorage.getItem(key) || '[]'); } catch (_) { hidden = []; }
    const apply = () => {
        document.querySelectorAll('[data-grid-column]').forEach((cell) => {
            cell.hidden = hidden.includes(cell.dataset.gridColumn);
        });
        toggles.forEach((toggle) => { toggle.checked = !hidden.includes(toggle.dataset.columnVisibility); });
    };
    toggles.forEach((toggle) => toggle.addEventListener('change', () => {
        const column = toggle.dataset.columnVisibility;
        hidden = toggle.checked ? hidden.filter((value) => value !== column) : [...new Set([...hidden, column])];
        localStorage.setItem(key, JSON.stringify(hidden));
        apply();
    }));
    apply();
})();
