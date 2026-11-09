(() => {
    const search = document.querySelector('[data-product-search]');
    const cards = [...document.querySelectorAll('[data-product-card]')];
    const noMatch = document.querySelector('[data-no-match]');

    const normalize = (value) => value.toLocaleLowerCase('es').normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '').trim();

    search?.addEventListener('input', () => {
        const query = normalize(search.value);
        let visible = 0;
        cards.forEach((card) => {
            const matches = !query || normalize(card.dataset.search || '').includes(query);
            card.hidden = !matches;
            if (matches) visible++;
        });
        if (noMatch) noMatch.hidden = visible !== 0;
    });

    const maxPhotoBytes = 4 * 1024 * 1024;
    const previews = [];

    document.querySelectorAll('[data-photo-form]').forEach((form) => {
        const input = form.querySelector('[data-photo-input]');
        const error = form.querySelector('[data-photo-error]');
        const container = form.closest('[data-product-card]')?.querySelector('.asset-photo');
        const originalImage = container?.querySelector('img');
        const placeholder = container?.querySelector('[data-photo-placeholder]');
        let image = originalImage;
        let objectUrl = null;

        const showError = (message) => {
            if (!error) return;
            error.textContent = message;
            error.hidden = !message;
        };

        const resetPreview = () => {
            if (objectUrl) URL.revokeObjectURL(objectUrl);
            objectUrl = null;
            if (!image) return;
            if (originalImage) image.src = image.dataset.originalSrc || image.src;
            else {
                image.remove();
                image = null;
                if (placeholder) placeholder.hidden = false;
            }
        };

        if (originalImage) originalImage.dataset.originalSrc = originalImage.src;
        previews.push(resetPreview);

        input?.addEventListener('change', () => {
            resetPreview();
            showError('');
            const file = input.files?.[0];
            if (!file) return;
            if (file.size > maxPhotoBytes) {
                showError('La foto no puede superar 4 MB.');
                input.value = '';
                return;
            }
            if (file.type && !['image/jpeg', 'image/png'].includes(file.type)) {
                showError('Elige una foto JPEG o PNG.');
                input.value = '';
                return;
            }
            if (!container) return;
            objectUrl = URL.createObjectURL(file);
            if (!image) {
                image = document.createElement('img');
                image.alt = 'Vista previa de la foto seleccionada';
                container.append(image);
                if (placeholder) placeholder.hidden = true;
            }
            image.src = objectUrl;
        });

        form.addEventListener('submit', (event) => {
            const file = input?.files?.[0];
            if (!file || file.size > maxPhotoBytes ||
                (file.type && !['image/jpeg', 'image/png'].includes(file.type))) {
                event.preventDefault();
                showError('Elige una foto JPEG o PNG de hasta 4 MB.');
            }
        });
    });

    document.querySelectorAll('[data-asset-form]').forEach((form) => {
        form.addEventListener('submit', (event) => {
            if (event.defaultPrevented) return;
            const button = form.querySelector('button[type="submit"]');
            if (button) {
                button.disabled = true;
                if (form.hasAttribute('data-photo-form')) button.textContent = 'Guardando...';
            }
        });
    });

    window.addEventListener('pagehide', () => previews.forEach((reset) => reset()));
})();
