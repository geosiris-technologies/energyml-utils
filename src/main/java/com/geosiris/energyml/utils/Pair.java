/*
Copyright 2019 GEOSIRIS

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package com.geosiris.energyml.utils;

public class Pair<TL, TR> {
	private final TL _l;
	private final TR _r;

	public Pair(TL a, TR b){
		_l = a;
		_r = b;
	}

	public TL l() {
		return _l;
	}

	public TR r() {
		return _r;
	}

	@Override
	public boolean equals(Object b){
		return b instanceof Pair
				&& ((this._l == null && ((Pair<?, ?>)b)._l == null)
					|| ((this._l != null && ((Pair<?, ?>)b)._l != null && this._l.equals(((Pair<?, ?>)b)._l)))
				)
				&& ((this._r == null && ((Pair<?, ?>)b)._r == null)
					|| ((this._r != null && ((Pair<?, ?>)b)._r != null && this._r.equals(((Pair<?, ?>)b)._r)))
				);
	}
}
