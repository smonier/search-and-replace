import React from 'react';
import {SearchAndReplace} from '../SearchAndReplace';
import PropTypes from 'prop-types';

export const AdminPanel = ({match}) => {
    return <SearchAndReplace match={match}/>;
};

AdminPanel.propTypes = {
    match: PropTypes.object
};

export default AdminPanel;
