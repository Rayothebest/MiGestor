(() => {
    const form = document.querySelector('[data-workflow-form]');
    if (!form) return;

    const trigger = form.querySelector('[data-wf-trigger]');
    const source = form.querySelector('[data-wf-source-module]');
    const target = form.querySelector('[data-wf-target-module]');
    const action = form.querySelector('[data-wf-action]');
    const rowSection = form.querySelector('[data-wf-row-trigger]');
    const saleSection = form.querySelector('[data-wf-sale-trigger]');
    const returnSection = form.querySelector('[data-wf-return-trigger]');
    const adjustSection = form.querySelector('[data-wf-adjust-action]');
    const createSection = form.querySelector('[data-wf-create-action]');
    const conditionColumn = form.querySelector('[data-wf-condition-column]');
    const conditionValue = form.querySelector('[data-wf-condition-value]');
    const relation = form.querySelector('[data-wf-relation]');
    const quantity = form.querySelector('[data-wf-quantity]');
    const stock = form.querySelector('[data-wf-stock]');
    const mappingList = form.querySelector('[data-wf-mappings]');
    const mappingTemplate = document.getElementById('workflow-mapping-template');
    const addMapping = form.querySelector('[data-wf-add-mapping]');

    const showSection = (element, visible) => {
        if (!element) return;
        element.hidden = !visible;
        element.querySelectorAll('input, select, textarea').forEach((field) => {
            field.disabled = !visible;
        });
    };

    const filterOptions = (select, allowed) => {
        if (!select) return;
        Array.from(select.options).forEach((option) => {
            if (!option.value) return;
            const visible = allowed(option);
            option.hidden = !visible;
            option.disabled = !visible;
        });
        if (select.selectedOptions[0]?.disabled) select.value = '';
    };

    const compatibleSource = (sourceType, targetType) => {
        if (!targetType) return true;
        if (targetType === 'TEXT') return true;
        if (targetType === 'SELECT') return sourceType === 'TEXT' || sourceType === 'SELECT';
        return sourceType === targetType;
    };

    const syncMapping = (row) => {
        const targetColumn = row.querySelector('[data-wf-mapping-target]');
        const sourceType = row.querySelector('[data-wf-mapping-type]');
        const sourceColumn = row.querySelector('[data-wf-mapping-source]');
        const literal = row.querySelector('[data-wf-mapping-literal]');
        const rowCreated = trigger.value === 'ROW_CREATED';
        const saleCreated = trigger.value === 'SALE_CREATED';
        const returnCreated = trigger.value === 'RETURN_CREATED';

        filterOptions(targetColumn, (option) => option.dataset.wfModule === target.value);
        const chosenType = targetColumn.selectedOptions[0]?.dataset.wfType || '';
        filterOptions(sourceType, (option) => {
            if (rowCreated) return option.value === 'SOURCE_COLUMN' || option.value === 'LITERAL';
            if (option.value === 'SOURCE_COLUMN') return false;
            if (option.value === 'SALE_ID') return chosenType === 'NUMBER' || chosenType === 'TEXT';
            if (option.value === 'SALE_TOTAL') return saleCreated && chosenType === 'NUMBER';
            if (option.value === 'RETURN_ID') return returnCreated && (chosenType === 'NUMBER' || chosenType === 'TEXT');
            if (option.value === 'RETURN_TOTAL') return returnCreated && chosenType === 'NUMBER';
            if (option.value === 'SALE_PAYMENT' || option.value === 'RETURN_METHOD') {
                if (option.value === 'SALE_PAYMENT' && !saleCreated) return false;
                if (option.value === 'RETURN_METHOD' && !returnCreated) return false;
                if (chosenType === 'TEXT') return true;
                if (chosenType !== 'SELECT') return false;
                const options = (targetColumn.selectedOptions[0]?.dataset.wfOptions || '')
                    .split(',').map((value) => value.trim());
                return ['CASH', 'CARD', 'TRANSFER', 'OTHER'].every((value) => options.includes(value));
            }
            return option.value === 'LITERAL';
        });
        const fromColumn = sourceType.value === 'SOURCE_COLUMN';
        const fromLiteral = sourceType.value === 'LITERAL';
        const sourceField = row.querySelector('[data-wf-mapping-source-field]');
        const literalField = row.querySelector('[data-wf-mapping-literal-field]');
        sourceField.hidden = !fromColumn;
        literalField.hidden = !fromLiteral;
        sourceColumn.required = fromColumn;
        literal.required = fromLiteral;
        if (!fromColumn) sourceColumn.value = '';
        if (!fromLiteral) literal.value = '';
        filterOptions(sourceColumn, (option) =>
            option.dataset.wfModule === source.value && compatibleSource(option.dataset.wfType, chosenType));
    };

    const bindMapping = (row) => {
        row.querySelector('[data-wf-remove-mapping]')?.addEventListener('click', () => {
            row.remove();
            if (!mappingList.querySelector('[data-wf-mapping]')) appendMapping();
        });
        row.querySelectorAll('select').forEach((select) => select.addEventListener('change', () => syncMapping(row)));
    };

    function appendMapping() {
        if (!mappingTemplate || mappingList.querySelectorAll('[data-wf-mapping]').length >= 40) return;
        const row = mappingTemplate.content.firstElementChild.cloneNode(true);
        mappingList.appendChild(row);
        bindMapping(row);
        syncMapping(row);
    }

    const sync = () => {
        const rowCreated = trigger.value === 'ROW_CREATED';
        const stockOption = action.querySelector('option[value="ADJUST_STOCK"]');
        stockOption.hidden = !rowCreated;
        stockOption.disabled = !rowCreated;
        if (!rowCreated && action.value === 'ADJUST_STOCK') action.value = 'CREATE_ROW';
        const adjusts = rowCreated && action.value === 'ADJUST_STOCK';
        const creates = action.value === 'CREATE_ROW';

        showSection(rowSection, rowCreated);
        showSection(saleSection, trigger.value === 'SALE_CREATED');
        showSection(returnSection, trigger.value === 'RETURN_CREATED');
        showSection(adjustSection, adjusts);
        showSection(createSection, creates);

        filterOptions(conditionColumn, (option) => option.dataset.wfModule === source.value);
        conditionValue.required = rowCreated && Boolean(conditionColumn.value);
        conditionValue.disabled = !rowCreated || !conditionColumn.value;
        if (!conditionColumn.value) conditionValue.value = '';
        filterOptions(relation, (option) =>
            option.dataset.wfModule === source.value && option.dataset.wfRelatedModule === target.value);
        filterOptions(quantity, (option) => option.dataset.wfModule === source.value);
        filterOptions(stock, (option) => option.dataset.wfModule === target.value);
        mappingList.querySelectorAll('[data-wf-mapping]').forEach(syncMapping);
    };

    mappingList.querySelectorAll('[data-wf-mapping]').forEach(bindMapping);
    if (!mappingList.querySelector('[data-wf-mapping]')) appendMapping();
    addMapping?.addEventListener('click', appendMapping);
    [trigger, source, target, action, conditionColumn].forEach((field) => field?.addEventListener('change', sync));
    sync();

    form.addEventListener('submit', (event) => {
        if (action.value !== 'CREATE_ROW') return;
        const mappings = Array.from(mappingList.querySelectorAll('[data-wf-mapping]'));
        const selected = mappings.map((row) => row.querySelector('[data-wf-mapping-target]').value).filter(Boolean);
        if (!selected.length) {
            event.preventDefault();
            alert('Agrega al menos una asignación para crear la fila.');
            return;
        }
        if (new Set(selected).size !== selected.length) {
            event.preventDefault();
            alert('Cada columna de destino sólo puede aparecer una vez.');
        }
    });
})();
