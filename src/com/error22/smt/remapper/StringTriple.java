package com.error22.smt.remapper;

public final class StringTriple {
	private final String cls, name, sig;

	public StringTriple(String cls, String name, String sig) {
		this.cls = cls;
		this.name = name;
		this.sig = sig;
	}

	public String getCls() {
		return cls;
	}

	public String getName() {
		return name;
	}

	public String getSig() {
		return sig;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((cls == null) ? 0 : cls.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((sig == null) ? 0 : sig.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		StringTriple other = (StringTriple) obj;
		if (cls == null) {
			if (other.cls != null)
				return false;
		} else if (!cls.equals(other.cls))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (sig == null) {
			if (other.sig != null)
				return false;
		} else if (!sig.equals(other.sig))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "StringTriple [class=" + cls + ", name=" + name + ", sig=" + sig + "]";
	}

}
