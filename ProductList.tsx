import React, { useState, useEffect } from "react";

interface Product {
  id: number;
  name: string;
  price: number;
  category: string;
}

interface ProductTableProps {
  data: Product[];
  onSort: (field: keyof Product) => void;
}

// Assume ProductTable is implemented elsewhere
declare const ProductTable: React.FC<ProductTableProps>;

/**
 * Interview Problem #4 — React / TypeScript
 * Difficulty: Medium | Issues to find: 2
 *
 * A product listing component that fetches from an API using a live
 * filter input and supports column sorting.
 * It renders correctly on first load and the sort buttons respond to clicks.
 * Find 2 things that are wrong or should be refactored.
 */
const ProductList: React.FC = () => {
  const [products, setProducts] = useState<Product[]>([]);
  const [filter, setFilter] = useState("");

  useEffect(() => {
    fetch(`/api/products?filter=${filter}`)
      .then((res) => res.json())
      .then((data) => setProducts(data));
  }, []);

  const handleSort = (field: keyof Product) => {
    products.sort((a, b) =>
      String(a[field]).localeCompare(String(b[field]))
    );
    setProducts(products);
  };

  return (
    <div>
      <input
        value={filter}
        onChange={(e) => setFilter(e.target.value)}
        placeholder="Filter products..."
      />
      <ProductTable data={products} onSort={handleSort} />
    </div>
  );
};

export default ProductList;
