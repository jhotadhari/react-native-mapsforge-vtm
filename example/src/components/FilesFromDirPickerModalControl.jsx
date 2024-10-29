/**
 * External dependencies
 */
import React, { useEffect, useState } from 'react';
import { sortBy } from 'lodash-es';

/**
 * Internal dependencies
 */
import PickerModalControl from './PickerModalControl.jsx';
import useDirInfo from '../compose/useDirInfo';

const FilesFromDirPickerModalControl = ( {
    hasSelectAll,
    dir,
    filePattern,
    disabled,
	buttonLabel,
	buttonLabelFallback,
	values,
	onChange,
	closeOnChange,
	headerLabel,
	itemHeight,
    sortReverse,
    NoOptionsComponent,
    extraOptions,
    ExtraOptionsHeader,
    OptionsHeader,
} ) => {

    const { navChildren } = useDirInfo( dir || null );

    const [options,setOptions] = useState( [] );

    useEffect( () => {
        const newOptions = Array.isArray( navChildren )
            ? sortBy( [...navChildren].filter( file => filePattern ? filePattern.test( file.name ) : true ), 'name' ).map( file => {
                return { value: file.name, label: file.name.split( '/' ).reverse()[0] };
            } )
            : [];
        setOptions( sortReverse ? newOptions.reverse() : newOptions );
    }, [navChildren] );

	return <PickerModalControl
        hasSelectAll={ hasSelectAll }
        options={ options }
        extraOptions={ extraOptions }
        NoOptionsComponent={ NoOptionsComponent }
        disabled={ disabled }
        buttonLabel={ buttonLabel }
        buttonLabelFallback={ buttonLabelFallback }
        values={ values }
        onChange={ onChange }
        closeOnChange={ closeOnChange }
        headerLabel={ headerLabel }
        itemHeight={ itemHeight }
        ExtraOptionsHeader={ ExtraOptionsHeader }
        OptionsHeader={ OptionsHeader }
    />;

};

export default FilesFromDirPickerModalControl;
