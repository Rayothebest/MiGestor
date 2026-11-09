(() => {
    const configForm = document.querySelector('[data-sales-config]');
    if (configForm) {
        const table = configForm.querySelector('[data-sales-table]');
        const columns = [...configForm.querySelectorAll('[data-sales-column]')];
        const syncColumns = () => {
            configForm.querySelector('[name="priceColumnId"]').setCustomValidity('');
            columns.forEach((select) => {
                [...select.options].forEach((option, index) => {
                    if (index === 0) return;
                    const available = option.dataset.salesTableId === table.value;
                    option.hidden = !available;
                    option.disabled = !available;
                });
                if (select.selectedOptions[0]?.disabled) select.value = '';
            });
        };
        table.addEventListener('change', syncColumns);
        columns.forEach((select) => select.addEventListener('change', () => {
            configForm.querySelector('[name="priceColumnId"]').setCustomValidity('');
        }));
        configForm.addEventListener('submit', (event) => {
            const stock = configForm.querySelector('[name="stockColumnId"]');
            const price = configForm.querySelector('[name="priceColumnId"]');
            price.setCustomValidity(stock.value && stock.value === price.value
                ? 'El precio y las existencias deben usar columnas distintas.' : '');
            if (!configForm.reportValidity()) event.preventDefault();
        });
        syncColumns();
    }

    const returnForm = document.querySelector('[data-return-form]');
    if (returnForm) {
        returnForm.addEventListener('submit', (event) => {
            const quantities = [...returnForm.querySelectorAll('[data-return-quantity]')];
            if (!quantities.some((field) => Number(field.value) > 0)) {
                event.preventDefault();
                alert('Indica la cantidad de al menos un artículo para devolver.');
                return;
            }
            if (!returnForm.reportValidity()) {
                event.preventDefault();
                return;
            }
            const submit = returnForm.querySelector('[data-return-submit]');
            submit.disabled = true;
            submit.textContent = 'Guardando devolución…';
        });
    }

    const form = document.querySelector('[data-sales-form]');
    if (!form) return;
    const lines = form.querySelector('[data-sale-lines]');
    const add = form.querySelector('[data-sale-add]');
    const total = form.querySelector('[data-sale-total]');
    const submit = form.querySelector('[data-sale-submit]');
    const format = new Intl.NumberFormat('es-MX', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    const barcodeInput = form.querySelector('[data-barcode-input]');
    const barcodeStatus = form.querySelector('[data-barcode-status]');
    const cameraButton = form.querySelector('[data-barcode-camera]');
    const cameraPanel = form.querySelector('[data-barcode-camera-panel]');
    const cameraVideo = form.querySelector('[data-barcode-video]');
    let cameraStream = null;
    let cameraActive = false;
    const normalizeCode = (value) => value.trim().toUpperCase();

    const newLine = () => {
        if (lines.querySelectorAll('[data-sale-line]').length >= 100) return null;
        const row = lines.querySelector('[data-sale-line]').cloneNode(true);
        row.querySelector('[data-sale-product]').value = '';
        row.querySelector('[data-sale-product]').setCustomValidity('');
        row.querySelector('[data-sale-quantity]').value = '1';
        row.querySelector('[data-sale-price]').value = '';
        row.querySelector('[data-sale-quantity]').setCustomValidity('');
        const preview = row.querySelector('[data-sale-product-preview]');
        preview.hidden = true;
        row.querySelector('[data-sale-product-img]').removeAttribute('src');
        lines.appendChild(row);
        refresh();
        return row;
    };

    const refresh = () => {
        const rows = [...lines.querySelectorAll('[data-sale-line]')];
        let sum = 0;
        rows.forEach((row) => {
            const product = row.querySelector('[data-sale-product]');
            const quantity = row.querySelector('[data-sale-quantity]');
            const price = row.querySelector('[data-sale-price]');
            const option = product.selectedOptions[0];
            const stock = Number(option?.dataset.stock);
            const qty = Number(quantity.value);
            const unit = Number(price.value);
            const lineTotal = Number.isFinite(qty * unit) && qty > 0 && unit >= 0 ? Math.round(qty * unit * 100) / 100 : 0;
            row.querySelector('[data-sale-line-total]').textContent = format.format(lineTotal);
            row.querySelector('[data-sale-stock]').textContent = product.value
                ? `Disponible: ${Number.isFinite(stock) ? stock : '—'}`
                : 'Selecciona un producto para ver sus existencias.';
            const preview = row.querySelector('[data-sale-product-preview]');
            const previewImage = row.querySelector('[data-sale-product-img]');
            if (product.value && option?.dataset.photo === 'true') {
                const photoUrl = `${form.dataset.photoBase}${encodeURIComponent(product.value)}`;
                if (previewImage.getAttribute('src') !== photoUrl) previewImage.src = photoUrl;
                preview.hidden = false;
            } else {
                preview.hidden = true;
                previewImage.removeAttribute('src');
            }
            quantity.setCustomValidity(product.value && Number.isFinite(stock) && qty > stock
                ? `Solo hay ${stock} unidades disponibles.` : '');
            sum += lineTotal;
            row.querySelector('[data-sale-remove]').disabled = rows.length === 1;
        });
        total.textContent = format.format(Math.round(sum * 100) / 100);
    };

    add.addEventListener('click', () => newLine()?.querySelector('[data-sale-product]').focus());

    const findBarcode = (rawCode) => {
        const code = normalizeCode(rawCode);
        if (!code) {
            barcodeStatus.textContent = 'Escribe o escanea un código.';
            barcodeInput.focus();
            return false;
        }
        const catalogOption = [...lines.querySelector('[data-sale-product]').options]
            .find((option) => option.value && normalizeCode(option.dataset.barcode || '') === code);
        if (!catalogOption) {
            barcodeStatus.textContent = `No se encontró un producto con el código ${code}.`;
            barcodeInput.select();
            return false;
        }
        const existing = [...lines.querySelectorAll('[data-sale-line]')]
            .find((row) => row.querySelector('[data-sale-product]').value === catalogOption.value);
        const row = existing || [...lines.querySelectorAll('[data-sale-line]')]
            .find((candidate) => !candidate.querySelector('[data-sale-product]').value) || newLine();
        if (!row) {
            barcodeStatus.textContent = 'La venta alcanzó el máximo de 100 productos.';
            return false;
        }
        const product = row.querySelector('[data-sale-product]');
        const quantity = row.querySelector('[data-sale-quantity]');
        if (existing) quantity.value = String(Number(quantity.value || 0) + 1);
        else {
            product.value = catalogOption.value;
            row.querySelector('[data-sale-price]').value = catalogOption.dataset.price || '';
            quantity.value = '1';
        }
        product.setCustomValidity('');
        refresh();
        barcodeStatus.textContent = existing
            ? `Se aumentó la cantidad de ${catalogOption.textContent.trim()}.`
            : `Se agregó ${catalogOption.textContent.trim()} a la venta.`;
        barcodeInput.value = '';
        barcodeInput.focus();
        return true;
    };
    form.querySelector('[data-barcode-find]').addEventListener('click', () => findBarcode(barcodeInput.value));
    barcodeInput.addEventListener('keydown', (event) => {
        if (event.key !== 'Enter') return;
        event.preventDefault();
        findBarcode(barcodeInput.value);
    });

    const stopCamera = () => {
        cameraActive = false;
        cameraStream?.getTracks().forEach((track) => track.stop());
        cameraStream = null;
        cameraVideo.srcObject = null;
        cameraPanel.hidden = true;
    };
    if ('BarcodeDetector' in window && navigator.mediaDevices?.getUserMedia) {
        cameraButton.hidden = false;
        cameraButton.addEventListener('click', async () => {
            if (cameraActive) return;
            cameraButton.disabled = true;
            barcodeStatus.textContent = 'Solicitando acceso a la cámara…';
            try {
                const supported = await BarcodeDetector.getSupportedFormats();
                const formats = ['ean_13', 'ean_8', 'upc_a', 'upc_e', 'code_128', 'code_39']
                    .filter((format) => supported.includes(format));
                if (!formats.length) throw new Error('Este navegador no admite formatos de códigos de productos.');
                const detector = new BarcodeDetector({ formats });
                cameraStream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' }, audio: false });
                cameraVideo.srcObject = cameraStream;
                cameraPanel.hidden = false;
                cameraActive = true;
                await cameraVideo.play();
                barcodeStatus.textContent = 'Apunta la cámara al código de barras.';
                while (cameraActive) {
                    const codes = await detector.detect(cameraVideo);
                    if (codes.length && findBarcode(codes[0].rawValue || '')) {
                        stopCamera();
                        break;
                    }
                    await new Promise((resolve) => setTimeout(resolve, 250));
                }
            } catch (error) {
                stopCamera();
                barcodeStatus.textContent = error.name === 'NotAllowedError'
                    ? 'No se autorizó la cámara. Puedes escribir el código o usar un lector USB.'
                    : `No se pudo usar la cámara. ${error.message || 'Escribe el código manualmente.'}`;
            } finally {
                cameraButton.disabled = false;
            }
        });
        form.querySelector('[data-barcode-stop]').addEventListener('click', () => {
            stopCamera();
            barcodeStatus.textContent = 'Cámara detenida. Puedes escribir el código manualmente.';
        });
        window.addEventListener('pagehide', stopCamera);
    }

    lines.addEventListener('click', (event) => {
        if (!event.target.matches('[data-sale-remove]')) return;
        if (lines.querySelectorAll('[data-sale-line]').length > 1) {
            event.target.closest('[data-sale-line]').remove();
            refresh();
        }
    });
    lines.addEventListener('change', (event) => {
        if (event.target.matches('[data-sale-product]')) {
            event.target.setCustomValidity('');
            const row = event.target.closest('[data-sale-line]');
            row.querySelector('[data-sale-price]').value = event.target.selectedOptions[0]?.dataset.price || '';
        }
        refresh();
    });
    lines.addEventListener('input', refresh);

    form.addEventListener('submit', (event) => {
        const selected = new Set();
        let duplicate = false;
        lines.querySelectorAll('[data-sale-product]').forEach((select) => {
            select.setCustomValidity('');
            if (!select.value) return;
            if (selected.has(select.value)) {
                select.setCustomValidity('Agrega cada producto una sola vez y ajusta su cantidad.');
                duplicate = true;
            }
            selected.add(select.value);
        });
        refresh();
        if (duplicate || !form.reportValidity()) {
            event.preventDefault();
            return;
        }
        submit.disabled = true;
        submit.textContent = 'Guardando venta…';
    });
    refresh();
})();
