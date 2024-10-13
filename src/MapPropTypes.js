

const MapPropTypes = {
	latLong: function( props, propName, componentName ) {
		if (
			! Array.isArray( props[propName] )																// is Array
			|| props[propName].length !== 2																	// is length 2
			|| ! [...props[propName]].reduce( ( acc, val ) => acc ? typeof val === 'number' : acc, true )	// all items is number
		) {
			return new Error( 'Invalid prop `' + propName + '` supplied to' + ' `' + componentName + '`. Validation failed.' );
		}
	},
};

export default MapPropTypes;
