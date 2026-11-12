import { describe, expect, it } from "vitest";
import { entries, prettyJson, roleLabel, roleScope } from "../src/utils/format";

describe("format", () => {
  it("labels roles", () => {
    expect(roleLabel("http://purl.imsglobal.org/vocab/lis/v2/membership#Instructor")).toBe("Instructor");
    expect(roleLabel("http://purl.imsglobal.org/vocab/lis/v2/system/person#User")).toBe("User");
    expect(roleLabel("Learner")).toBe("Learner");
    expect(roleLabel("http://example.com/")).toBe("http://example.com/");
    expect(roleLabel(null)).toBe("");
  });

  it("finds a role's scope", () => {
    expect(roleScope("http://purl.imsglobal.org/vocab/lis/v2/institution/person#Student")).toBe("institution");
    expect(roleScope("Learner")).toBeNull();
    expect(roleScope(undefined)).toBeNull();
  });

  it("pretty prints JSON", () => {
    expect(prettyJson({ a: 1 })).toBe('{\n  "a": 1\n}');
    expect(prettyJson(undefined)).toBe("null");
  });

  it("turns an object into rows", () => {
    expect(entries({ a: "x", b: 2, c: { d: 1 }, e: null })).toEqual([
      { key: "a", value: "x" },
      { key: "b", value: "2" },
      { key: "c", value: '{"d":1}' },
      { key: "e", value: "null" }
    ]);
    expect(entries(null)).toEqual([]);
    expect(entries("text")).toEqual([]);
  });
});
