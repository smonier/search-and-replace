import React from 'react';

export const escapeRegExp = value => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

export const isHtmlContent = value => {
    if (!value || typeof value !== 'string') {
        return false;
    }

    return /<\/?[a-z][\s\S]*>/i.test(value);
};

export const sanitizeAndHighlightHtml = (rawHtml, term, highlightClassName) => {
    if (!rawHtml || typeof window === 'undefined') {
        return rawHtml;
    }

    const parser = new window.DOMParser();
    const doc = parser.parseFromString(rawHtml, 'text/html');

    doc.querySelectorAll('script, iframe, object, embed').forEach(node => node.remove());
    doc.querySelectorAll('*').forEach(node => {
        Array.from(node.attributes).forEach(attribute => {
            const attributeName = attribute.name.toLowerCase();
            const attributeValue = attribute.value || '';

            if (attributeName.startsWith('on')) {
                node.removeAttribute(attribute.name);
                return;
            }

            if ((attributeName === 'href' || attributeName === 'src') && /^javascript:/i.test(attributeValue.trim())) {
                node.removeAttribute(attribute.name);
            }
        });
    });

    const normalizedTerm = (term || '').trim();
    if (!normalizedTerm) {
        return doc.body.innerHTML;
    }

    const searchTermLower = normalizedTerm.toLowerCase();
    const textNodes = [];
    const walker = doc.createTreeWalker(doc.body, window.NodeFilter.SHOW_TEXT);

    let currentNode = walker.nextNode();
    while (currentNode) {
        textNodes.push(currentNode);
        currentNode = walker.nextNode();
    }

    textNodes.forEach(textNode => {
        const textContent = textNode.nodeValue || '';
        const lowerText = textContent.toLowerCase();
        if (!lowerText.includes(searchTermLower)) {
            return;
        }

        const fragment = doc.createDocumentFragment();
        let cursor = 0;
        let matchIndex = lowerText.indexOf(searchTermLower, cursor);

        while (matchIndex !== -1) {
            if (matchIndex > cursor) {
                fragment.appendChild(doc.createTextNode(textContent.slice(cursor, matchIndex)));
            }

            const matchedText = textContent.slice(matchIndex, matchIndex + normalizedTerm.length);
            const markElement = doc.createElement('mark');
            markElement.className = highlightClassName;
            markElement.textContent = matchedText;
            fragment.appendChild(markElement);

            cursor = matchIndex + normalizedTerm.length;
            matchIndex = lowerText.indexOf(searchTermLower, cursor);
        }

        if (cursor < textContent.length) {
            fragment.appendChild(doc.createTextNode(textContent.slice(cursor)));
        }

        if (textNode.parentNode) {
            textNode.parentNode.replaceChild(fragment, textNode);
        }
    });

    return doc.body.innerHTML;
};

export const renderHighlightedText = (text, term, highlightClassName, keyPrefix = 'highlight') => {
    if (!text || !term) {
        return text;
    }

    const regex = new RegExp(`(${escapeRegExp(term)})`, 'gi');
    const parts = text.split(regex);

    return (
        <span>
            {/* eslint-disable react/no-array-index-key */}
            {parts.map((part, index) =>
                part.toLowerCase() === term.toLowerCase() ?
                    <mark key={`${keyPrefix}-${index}`} className={highlightClassName}>{part}</mark> :
                    <span key={`text-${keyPrefix}-${index}`}>{part}</span>
            )}
            {/* eslint-enable react/no-array-index-key */}
        </span>
    );
};
