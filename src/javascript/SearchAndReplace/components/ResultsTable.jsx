/* eslint-disable react/no-danger */
import React, {useState, useCallback} from 'react';
import {useTranslation} from 'react-i18next';
import {
    Table,
    TableBody,
    TableHead,
    TableRow,
    TableHeadCell,
    TableBodyCell,
    Checkbox,
    Typography,
    Chip,
    Button,
    ChevronDown,
    ChevronUp
} from '@jahia/moonstone';
import PropTypes from 'prop-types';
import dayjs from 'dayjs';
import styles from '../SearchAndReplace.scss';
import {isHtmlContent, renderHighlightedText, sanitizeAndHighlightHtml} from '../utils/highlight.utils';

/**
 * Results Table Component
 * Displays search results in a table with selection capabilities
 */
export const ResultsTable = ({nodes, searchTerm, selectedLanguage, siteKey, selectedNodes, onSelectionChange}) => {
    const {t} = useTranslation('search-and-replace');
    const [expandedNodes, setExpandedNodes] = useState(new Set());
    const [selectAll, setSelectAll] = useState(false);

    // Toggle node expansion
    const toggleExpand = useCallback(uuid => {
        setExpandedNodes(prev => {
            const newSet = new Set(prev);
            if (newSet.has(uuid)) {
                newSet.delete(uuid);
            } else {
                newSet.add(uuid);
            }

            return newSet;
        });
    }, []);

    // Handle individual selection
    const handleSelectNode = useCallback((uuid, checked) => {
        if (checked) {
            onSelectionChange([...selectedNodes, uuid]);
        } else {
            onSelectionChange(selectedNodes.filter(id => id !== uuid));
            setSelectAll(false);
        }
    }, [selectedNodes, onSelectionChange]);

    // Handle select all
    const handleSelectAll = useCallback(checked => {
        setSelectAll(checked);
        if (checked) {
            onSelectionChange(nodes.map(node => node.uuid));
        } else {
            onSelectionChange([]);
        }
    }, [nodes, onSelectionChange]);

    // Format date
    const formatDate = useCallback(dateString => {
        return dayjs(dateString).format('YYYY-MM-DD HH:mm');
    }, []);

    const buildParentLink = useCallback(parentContainerPath => {
        if (!parentContainerPath || !siteKey || !selectedLanguage) {
            return '';
        }

        const origin = window?.location?.origin || '';
        const contextPath = window?.contextJsParameters?.contextPath || '';
        const sitePrefix = `/sites/${siteKey}/`;
        let relativePath = parentContainerPath.startsWith(sitePrefix) ?
            parentContainerPath.substring(sitePrefix.length) :
            parentContainerPath.replace(/^\/+/, '');

        relativePath = relativePath.replace(/^\/+|\/+$/g, '');

        const section = relativePath === 'contents' || relativePath.startsWith('contents/') ?
            'content-folders' :
            'pages';
        const suffix = relativePath ? `/${relativePath}` : '';

        return `${origin}${contextPath}/jahia/jcontent/${siteKey}/${selectedLanguage}/${section}${suffix}?`;
    }, [selectedLanguage, siteKey]);

    return (
        <div className={styles.resultsTableContainer}>
            <Table className={styles.resultsTable} aria-label={t('label.resultsTable')}>
                <TableHead>
                    <TableRow>
                        <TableHeadCell>
                            <Checkbox
                                checked={selectAll}
                                onChange={e => handleSelectAll(e.target.checked)}
                            />
                        </TableHeadCell>
                        <TableHeadCell>{t('label.name')}</TableHeadCell>
                        <TableHeadCell>{t('label.contentType')}</TableHeadCell>
                        <TableHeadCell>{t('label.path')}</TableHeadCell>
                        <TableHeadCell>{t('label.created')}</TableHeadCell>
                        <TableHeadCell>{t('label.modified')}</TableHeadCell>
                        <TableHeadCell>{t('label.matches')}</TableHeadCell>
                        <TableHeadCell/>
                    </TableRow>
                </TableHead>
                <TableBody>
                    {nodes.map(node => {
                        if (!node || !node.uuid) {
                            return null;
                        }

                        const isSelected = selectedNodes.includes(node.uuid);
                        const isExpanded = expandedNodes.has(node.uuid);
                        const matchingProps = (node.matchingProperties || []).filter(p => p && p.name);
                        const replaceableProps = matchingProps.filter(p => p.replaceable);

                        return (
                            <React.Fragment key={node.uuid}>
                                <TableRow className={isSelected ? styles.selectedRow : ''}>
                                    <TableBodyCell>
                                        <Checkbox
                                            checked={isSelected}
                                            onChange={e => handleSelectNode(node.uuid, e.target.checked)}
                                        />
                                    </TableBodyCell>
                                    <TableBodyCell>
                                        <Typography variant="body" weight="bold">
                                            {node.displayName || node.name}
                                        </Typography>
                                    </TableBodyCell>
                                    <TableBodyCell>
                                        <Chip label={node.nodeTypeLabel} size="small"/>
                                    </TableBodyCell>
                                    <TableBodyCell>
                                        <Typography variant="caption" className={styles.pathText}>
                                            {node.path}
                                        </Typography>
                                        {node.parentContainerPath && (
                                            <a
                                                href={buildParentLink(node.parentContainerPath)}
                                                target="_blank"
                                                rel="noreferrer"
                                                className={styles.parentLink}
                                            >
                                                {t('label.openParent', {path: node.parentContainerPath})}
                                            </a>
                                        )}
                                    </TableBodyCell>
                                    <TableBodyCell>
                                        <Typography variant="caption">
                                            {formatDate(node.created)}
                                        </Typography>
                                    </TableBodyCell>
                                    <TableBodyCell>
                                        <Typography variant="caption">
                                            {formatDate(node.lastModified)}
                                        </Typography>
                                    </TableBodyCell>
                                    <TableBodyCell>
                                        <div className={styles.matchesBadges}>
                                            <Chip
                                                label={`${matchingProps.length} ${t('label.properties')}`}
                                                color="accent"
                                                size="small"
                                            />
                                            <Chip
                                                label={t('label.replaceableRatio', {
                                                    replaceable: replaceableProps.length,
                                                    total: matchingProps.length
                                                })}
                                                color="default"
                                                size="small"
                                            />
                                        </div>
                                    </TableBodyCell>
                                    <TableBodyCell>
                                        <Button
                                            icon={isExpanded ? <ChevronUp/> : <ChevronDown/>}
                                            variant="ghost"
                                            size="small"
                                            onClick={() => toggleExpand(node.uuid)}
                                        />
                                    </TableBodyCell>
                                </TableRow>
                                {isExpanded && (
                                    <TableRow className={styles.expandedRow}>
                                        <TableBodyCell colSpan={8} className={styles.expandedRowCell}>
                                            <div className={styles.propertiesPreview}>
                                                <Typography variant="subheading" weight="bold">
                                                    {t('label.matchingProperties')}
                                                </Typography>
                                                <div className={styles.propertiesList}>
                                                    {(node.matchingProperties || []).filter(prop => prop && prop.name).map(prop => (
                                                        <div key={`${node.uuid}-${prop.name}`} className={styles.propertyItem}>
                                                            <div className={styles.propertyHeader}>
                                                                <Typography variant="caption" weight="bold">
                                                                    {prop.label || prop.name}
                                                                </Typography>
                                                                {!prop.replaceable && (
                                                                    <Chip
                                                                        label={t('label.readOnly')}
                                                                        color="warning"
                                                                        size="small"
                                                                    />
                                                                )}
                                                            </div>
                                                            {isHtmlContent(prop.value) ? (
                                                                <div
                                                                    dangerouslySetInnerHTML={{__html: sanitizeAndHighlightHtml(prop.value, searchTerm, styles.highlight)}}
                                                                    className={styles.propertyValueHtml}
                                                                />
                                                            ) : (
                                                                <Typography variant="body" className={styles.propertyValue}>
                                                                    {renderHighlightedText(prop.value, searchTerm, styles.highlight, `results-${node.uuid}`)}
                                                                </Typography>
                                                            )}
                                                        </div>
                                                    ))}
                                                </div>
                                            </div>
                                        </TableBodyCell>
                                    </TableRow>
                                )}
                            </React.Fragment>
                        );
                    })}
                </TableBody>
            </Table>
        </div>
    );
};

ResultsTable.propTypes = {
    nodes: PropTypes.array.isRequired,
    searchTerm: PropTypes.string.isRequired,
    selectedLanguage: PropTypes.string,
    siteKey: PropTypes.string,
    selectedNodes: PropTypes.array.isRequired,
    onSelectionChange: PropTypes.func.isRequired
};

export default ResultsTable;
